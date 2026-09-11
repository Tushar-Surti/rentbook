package com.rentbook.payment;

import com.rentbook.common.ApiException;
import com.rentbook.config.RentbookProperties;
import com.rentbook.user.LandlordProfile;
import com.rentbook.user.LandlordProfileRepository;
import com.rentbook.user.User;
import com.rentbook.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Onboards a landlord as a Razorpay Route linked account in four calls: account, stakeholder, the
 * Route product, then its settlement bank account. Progress is saved after every call, so a retry
 * after a failure resumes where it stopped instead of creating a second account at Razorpay.
 */
@Service
public class PayoutService {

    private static final Logger log = LoggerFactory.getLogger(PayoutService.class);

    private final PayoutAccountRepository accounts;
    private final UserRepository users;
    private final LandlordProfileRepository landlordProfiles;
    private final RazorpayGateway gateway;
    private final RazorpayProperties razorpay;
    private final RentbookProperties rentbook;

    PayoutService(PayoutAccountRepository accounts, UserRepository users, LandlordProfileRepository landlordProfiles,
                  RazorpayGateway gateway, RazorpayProperties razorpay, RentbookProperties rentbook) {
        this.accounts = accounts;
        this.users = users;
        this.landlordProfiles = landlordProfiles;
        this.gateway = gateway;
        this.razorpay = razorpay;
        this.rentbook = rentbook;
    }

    /** {@code phone} is only needed when the landlord's account has none; Razorpay requires one. */
    public record Onboarding(String legalName, String pan, String phone, String street, String city, String state,
                             String postalCode, String accountNumber, String ifsc, String beneficiaryName) {
    }

    @Transactional(readOnly = true)
    public Optional<PayoutAccount> account(UUID landlordId) {
        return accounts.findById(landlordId);
    }

    /** Whether this landlord's tenants can pay online right now. */
    @Transactional(readOnly = true)
    public boolean acceptsOnlinePayments(UUID landlordId) {
        return razorpay.configured() && accounts.findById(landlordId).filter(PayoutAccount::isActive).isPresent();
    }

    /** The platform's cut of each payment, in basis points, borne by the landlord. */
    @Transactional(readOnly = true)
    public int feeBps(UUID landlordId) {
        return landlordProfiles.findById(landlordId).map(LandlordProfile::getPlatformFeeBps)
                .orElse(rentbook.platformFeeBps());
    }

    /** Deliberately not one transaction: each remote step's result is committed before the next call. */
    public PayoutAccount onboard(UUID landlordId, Onboarding details) {
        requireConfigured();
        User landlord = users.findById(landlordId).orElseThrow(() -> ApiException.notFound("Landlord"));
        String phone = landlord.getPhone() != null ? landlord.getPhone() : details.phone();
        if (phone == null || phone.isBlank()) {
            throw ApiException.badRequest("phone_required", "Add your mobile number; Razorpay needs one.");
        }
        PayoutAccount account = accounts.findById(landlordId).orElseGet(() -> new PayoutAccount(landlordId));
        account.describe(details.legalName(), details.beneficiaryName(), details.ifsc(), details.accountNumber());
        account = accounts.save(account);
        if (details.pan() != null) {
            // Printed on the landlord's rent receipts from now on; tenants need it for HRA claims.
            landlordProfiles.findById(landlordId).ifPresent(profile -> {
                profile.recordPan(details.pan());
                landlordProfiles.save(profile);
            });
        }
        try {
            if (account.getRzpAccountId() == null) {
                account.linkedAccount(gateway.createLinkedAccount(new RazorpayGateway.LinkedAccountRequest(
                        landlord.getEmail(), phone, details.legalName(), landlord.getFullName(),
                        details.street(), details.city(), details.state(), details.postalCode(), details.pan())));
                account = accounts.save(account);
            }
            if (account.getRzpStakeholderId() == null) {
                account.stakeholder(gateway.createStakeholder(account.getRzpAccountId(), landlord.getFullName(),
                        landlord.getEmail()));
                account = accounts.save(account);
            }
            if (account.getRzpProductId() == null) {
                RazorpayGateway.RouteProduct product = gateway.requestRouteProduct(account.getRzpAccountId());
                account.routeProduct(product.id(), product.activationStatus());
                account = accounts.save(account);
            }
            RazorpayGateway.RouteProduct product = gateway.updateSettlement(account.getRzpAccountId(),
                    account.getRzpProductId(), new RazorpayGateway.SettlementAccount(details.accountNumber(),
                            details.ifsc(), details.beneficiaryName()));
            account.status(product.activationStatus());
            return accounts.save(account);
        } catch (RazorpayException e) {
            if (e.isAuthFailure()) {
                log.error("Razorpay rejected the API keys during Route onboarding; check RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET");
                throw ApiException.serviceUnavailable("payments_misconfigured",
                        "Razorpay didn't accept this server's keys, so payouts can't be set up yet.");
            }
            log.warn("Route onboarding for landlord {} stopped at Razorpay", landlordId, e);
            throw ApiException.badGateway("razorpay_rejected",
                    "Razorpay didn't accept those details. Check them and try again.");
        }
    }

    /**
     * Local development only: attach a linked account created by hand in the Razorpay test dashboard,
     * for when test-mode onboarding through the API is not enabled on the platform account.
     */
    @Transactional
    public PayoutAccount linkExisting(UUID landlordId, String accountId, String legalName) {
        PayoutAccount account = accounts.findById(landlordId).orElseGet(() -> new PayoutAccount(landlordId));
        account.describe(legalName, legalName, "TEST0000000", "0000");
        account.linkedAccount(accountId);
        account.status("activated");
        return accounts.save(account);
    }

    void requireConfigured() {
        if (!razorpay.configured()) {
            throw ApiException.serviceUnavailable("payments_off",
                    "Online payments aren't set up on this server yet.");
        }
    }
}
