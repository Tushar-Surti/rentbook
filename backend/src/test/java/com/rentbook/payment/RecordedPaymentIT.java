package com.rentbook.payment;

import com.jayway.jsonpath.JsonPath;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.rentbook.Flows;
import com.rentbook.IntegrationTest;
import com.rentbook.ledger.LedgerService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rent the landlord was paid directly, recorded in the book with a receipt, and never charged twice. */
class RecordedPaymentIT extends IntegrationTest {

    @Autowired
    LedgerService ledger;

    @Autowired
    JdbcTemplate jdbc;

    private record Tenancy(Flows.Session landlord, Flows.Session tenant, String leaseId) {
        String ledgerPath() {
            return "/api/v1/leases/" + leaseId + "/ledger";
        }

        String paymentsPath() {
            return "/api/v1/leases/" + leaseId + "/payments";
        }

        String receiptsPath() {
            return "/api/v1/leases/" + leaseId + "/receipts";
        }
    }

    @Test
    void cashTheLandlordRecordsSettlesTheLedgerForBothWithAReceipt() throws Exception {
        Tenancy home = moveIn();
        List<String> due = dueChargeIds(home);
        assertThat(due).hasSize(2);

        record(home, due, "CASH", today(), "Paid at the door")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receiptNumber").value("0001"))
                .andExpect(jsonPath("$.amountPaise").value(3_750_000));

        flows.get(home.tenant(), home.ledgerPath())
                .andExpect(jsonPath("$.outstandingPaise").value(0))
                .andExpect(jsonPath("$.entries[*].status", everyItem(is("PAID"))));
        flows.get(home.tenant(), home.receiptsPath())
                .andExpect(jsonPath("$[0].method").value("CASH"))
                .andExpect(jsonPath("$[0].receivedOn").value(today().toString()));

        // Paid to the landlord directly, so Rentbook takes no fee.
        Map<String, Object> row = jdbc.queryForMap(
                "select method, platform_fee_paise, landlord_share_paise, status from payments where lease_id = ?",
                UUID.fromString(home.leaseId()));
        assertThat(row).containsEntry("method", "CASH").containsEntry("platform_fee_paise", 0L)
                .containsEntry("landlord_share_paise", 3_750_000L).containsEntry("status", "CAPTURED");

        String receiptId = JsonPath.read(flows.get(home.tenant(), home.receiptsPath())
                .andReturn().getResponse().getContentAsString(), "$[0].id");
        byte[] pdf = flows.get(home.tenant(), "/api/v1/receipts/" + receiptId + "/pdf")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        String text = new PdfTextExtractor(new PdfReader(pdf)).getTextFromPage(1).replaceAll("\\s+", " ");
        assertThat(text).contains("No. 0001", "Received in cash on", "(Paid at the door)", "Recorded by the landlord")
                .doesNotContain("Razorpay");

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, timeout(5_000).atLeastOnce()).send(sent.capture());
        assertThat(sent.getAllValues()).anySatisfy(message -> {
            assertThat(message.getSubject()).isEqualTo("Payment of ₹37,500 recorded, receipt 0001");
            assertThat(message.getText()).contains("Lata Iyer recorded your payment of ₹37,500, paid in cash.");
        });
    }

    @Test
    void onlyTheLandlordRecordsAndOnlyWhatIsStillDue() throws Exception {
        Tenancy home = moveIn();
        Tenancy other = moveIn();
        List<String> due = dueChargeIds(home);

        // The tenant can't mark their own rent paid; another landlord can't see the lease.
        flows.authed(post(home.paymentsPath()), home.tenant(), body(due, "CASH", today(), null))
                .andExpect(status().isForbidden());
        record(other, due, "CASH", today(), null).andExpect(status().isNotFound());
        flows.authed(post(home.paymentsPath()), other.landlord(), body(due, "CASH", today(), null))
                .andExpect(status().isNotFound());

        record(home, due, "UPI", today().plusDays(1), null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("received_in_future"));
        record(home, due, "RAZORPAY", today(), null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("wrong_method"));
        record(home, List.of(), "CASH", today(), null).andExpect(status().isBadRequest());
        record(home, dueChargeIds(other), "CASH", today(), null).andExpect(status().isNotFound());

        record(home, due.subList(0, 1), "UPI", today(), "UPI ref 4412").andExpect(status().isCreated());
        record(home, due, "CASH", today(), null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("already_settled"));
        record(home, due.subList(1, 2), "CHEQUE", today().minusDays(2), null)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.receiptNumber").value("0002"));
        flows.get(home.landlord(), home.ledgerPath()).andExpect(jsonPath("$.outstandingPaise").value(0));
    }

    @Test
    void anOnlinePaymentStillWaitingForTheBankCannotAlsoBeRecorded() throws Exception {
        Tenancy home = moveIn();
        List<String> due = dueChargeIds(home);
        UUID paymentId = UUID.randomUUID();
        Timestamp now = Timestamp.from(CLOCK.instant());
        jdbc.update("""
                insert into payments (id, lease_id, tenant_id, landlord_id, amount_paise, platform_fee_paise,
                    landlord_share_paise, rzp_order_id, status, created_at, updated_at)
                values (?, ?, ?, ?, 1250000, 25000, 1225000, ?, 'AWAITING_WEBHOOK', ?, ?)""",
                paymentId, UUID.fromString(home.leaseId()), UUID.fromString(home.tenant().userId()),
                UUID.fromString(home.landlord().userId()), "order_" + paymentId.toString().substring(0, 8), now, now);
        jdbc.update("insert into payment_charges (payment_id, charge_id) values (?, ?)", paymentId, UUID.fromString(due.get(0)));

        record(home, due, "CASH", today(), null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("payment_pending"));
        flows.get(home.tenant(), home.ledgerPath()).andExpect(jsonPath("$.outstandingPaise").value(3_750_000));
    }

    private Tenancy moveIn() throws Exception {
        Flows.Session landlord = flows.registerLandlord(Flows.email("lata"));
        String pg = flows.createProperty(landlord, "Recorded PG");
        String flat = flows.addUnit(landlord, pg, "FLAT", "Flat 2B", null);
        Flows.Session tenant = flows.acceptAsNewTenant(flows.inviteToken(landlord, flat, Flows.email("asha"), today()));
        return new Tenancy(landlord, tenant, flows.leaseIdFor(tenant));
    }

    private List<String> dueChargeIds(Tenancy home) throws Exception {
        String body = flows.get(home.landlord(), home.ledgerPath()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.entries[?(@.status in ['DUE', 'OVERDUE', 'UPCOMING'])].id");
    }

    private ResultActions record(Tenancy home, List<String> chargeIds, String method, LocalDate receivedOn, String note)
            throws Exception {
        return flows.authed(post(home.paymentsPath()), home.landlord(), body(chargeIds, method, receivedOn, note));
    }

    private static String body(List<String> chargeIds, String method, LocalDate receivedOn, String note) {
        String ids = String.join("\",\"", chargeIds);
        return """
                {"chargeIds":[%s],"method":"%s","receivedOn":"%s","note":%s}"""
                .formatted(chargeIds.isEmpty() ? "" : "\"" + ids + "\"", method, receivedOn,
                        note == null ? "null" : "\"" + note + "\"");
    }

    private LocalDate today() {
        return ledger.today();
    }
}
