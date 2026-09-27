package com.rentbook.payment;

import com.rentbook.common.ApiException;
import com.rentbook.common.IndiaTime;
import com.rentbook.common.Rupees;
import com.rentbook.lease.LeaseService;
import com.rentbook.ledger.Charge;
import com.rentbook.ledger.ChargeRepository;
import com.rentbook.property.Property;
import com.rentbook.property.PropertyRepository;
import com.rentbook.user.LandlordProfile;
import com.rentbook.user.LandlordProfileRepository;
import com.rentbook.user.Role;
import com.rentbook.user.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Receipts for confirmed payments: issued with the next number in the landlord's book, read by both parties. */
@Service
public class ReceiptService {

    // en-IN, as the app prints dates: "11 Sept 2026".
    private static final Locale INDIA = Locale.of("en", "IN");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", INDIA);
    private static final DateTimeFormatter MOMENT = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", INDIA);

    private final JdbcTemplate jdbc;
    private final ReceiptRepository receipts;
    private final PaymentRepository payments;
    private final ChargeRepository charges;
    private final LeaseService leases;
    private final PropertyRepository properties;
    private final LandlordProfileRepository landlordProfiles;
    private final ReceiptPdfRenderer renderer;
    private final UserRepository users;
    private final Clock clock;

    ReceiptService(JdbcTemplate jdbc, ReceiptRepository receipts, PaymentRepository payments, ChargeRepository charges,
                   LeaseService leases, PropertyRepository properties, LandlordProfileRepository landlordProfiles,
                   ReceiptPdfRenderer renderer, UserRepository users, Clock clock) {
        this.jdbc = jdbc;
        this.receipts = receipts;
        this.payments = payments;
        this.charges = charges;
        this.leases = leases;
        this.properties = properties;
        this.landlordProfiles = landlordProfiles;
        this.renderer = renderer;
        this.users = users;
        this.clock = clock;
    }

    /** {@code method} says how it was paid; {@code receivedOn} is set when the landlord recorded it. */
    public record ReceiptView(UUID id, String number, Instant issuedAt, long amountPaise, String paymentReference,
                              List<String> items, Payment.Method method, LocalDate receivedOn) {
    }

    public record ReceiptFile(String filename, byte[] pdf) {
    }

    /** Runs inside the confirming transaction; the counter row lock numbers concurrent receipts safely. */
    @Transactional
    Receipt issue(Payment payment) {
        return receipts.findByPaymentId(payment.getId()).orElseGet(() -> {
            Integer serial = jdbc.queryForObject(
                    "update landlord_profiles set receipt_seq = receipt_seq + 1 where user_id = ? returning receipt_seq",
                    Integer.class, payment.getLandlordId());
            return receipts.save(new Receipt(payment.getId(), payment.getLandlordId(), serial, clock.instant()));
        });
    }

    @Transactional(readOnly = true)
    public List<ReceiptView> forLease(UUID leaseId, UUID userId, Role role) {
        leases.require(leaseId, userId, role);
        return receipts.findForLease(leaseId).stream().map(this::view).toList();
    }

    /** The lease's latest receipt. The caller has already established that the reader is a party to it. */
    @Transactional(readOnly = true)
    public Optional<ReceiptView> latestForLease(UUID leaseId) {
        return receipts.findForLease(leaseId).stream().findFirst().map(this::view);
    }

    private ReceiptView view(Receipt receipt) {
        Payment payment = payments.findById(receipt.getPaymentId()).orElseThrow();
        List<String> items = charges.findAllById(payment.getChargeIds()).stream()
                .sorted(Comparator.comparing(Charge::getDueOn)).map(Charge::getDescription).toList();
        return new ReceiptView(receipt.getId(), receipt.number(), receipt.getIssuedAt(), payment.getAmountPaise(),
                payment.getRzpPaymentId(), items, payment.getMethod(), payment.getReceivedOn());
    }

    @Transactional(readOnly = true)
    public ReceiptFile pdf(UUID receiptId, UUID userId, Role role) {
        Receipt receipt = receipts.findById(receiptId).orElseThrow(() -> ApiException.notFound("Receipt"));
        Payment payment = payments.findById(receipt.getPaymentId()).orElseThrow(() -> ApiException.notFound("Receipt"));
        LeaseService.LeaseView lease;
        try {
            lease = leases.view(payment.getLeaseId(), userId, role);
        } catch (ApiException notTheirs) {
            throw ApiException.notFound("Receipt");
        }
        List<ReceiptPdfRenderer.Line> lines = charges.findAllById(payment.getChargeIds()).stream()
                .sorted(Comparator.comparing(Charge::getDueOn))
                .map(charge -> new ReceiptPdfRenderer.Line(charge.getDescription(), Rupees.format(charge.getAmountPaise())))
                .toList();
        String pan = landlordProfiles.findById(payment.getLandlordId()).map(LandlordProfile::getPan).orElse(null);
        byte[] pdf = renderer.render(new ReceiptPdfRenderer.ReceiptDocument(
                receipt.number(),
                DAY.format(receipt.getIssuedAt().atZone(IndiaTime.ZONE)),
                lease.tenant().fullName(),
                lease.landlord().fullName(),
                pan,
                home(lease),
                Rupees.format(payment.getAmountPaise()),
                IndianNumberWords.rupees(payment.getAmountPaise()),
                lines,
                proof(payment)));
        return new ReceiptFile("rentbook-receipt-" + receipt.number() + ".pdf", pdf);
    }

    /** How the money was received, as the receipt states it. */
    private String proof(Payment payment) {
        String at = MOMENT.format(payment.getCapturedAt().atZone(IndiaTime.ZONE));
        if (!payment.isRecordedByLandlord()) {
            return "Received online through Razorpay (payment " + payment.getRzpPaymentId()
                    + "), confirmed by Razorpay's signed notification on " + at + ".";
        }
        if (payment.getMethod() == Payment.Method.DEPOSIT) {
            return "Paid from the security deposit the landlord held, as both agreed in the deposit settlement on "
                    + DAY.format(payment.getReceivedOn()) + ".";
        }
        String note = payment.getNote() == null || payment.getNote().isBlank() ? "" : " (" + payment.getNote().strip() + ")";
        String by = payment.getRecordedBy() == null || payment.getRecordedBy().equals(payment.getLandlordId())
                ? "the landlord"
                : users.findById(payment.getRecordedBy()).map(user -> user.getFullName() + ", the landlord's caretaker,")
                        .orElse("the landlord's caretaker");
        return "Received " + payment.getMethod().phrase() + " on " + DAY.format(payment.getReceivedOn()) + note
                + ". Recorded by " + by + " on " + at + ".";
    }

    /** The full address of the home, as an HRA claim asks for it: unit, building, street, city and PIN. */
    private String home(LeaseService.LeaseView lease) {
        String unit = lease.unit().roomLabel() == null
                ? lease.unit().label() : lease.unit().label() + ", " + lease.unit().roomLabel();
        Property property = properties.findById(lease.property().id()).orElseThrow();
        return unit + ", " + property.getName() + ", " + property.getAddressLine() + ", " + property.getCity() + " "
                + property.getPincode();
    }
}
