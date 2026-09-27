package com.rentbook.payment;

import com.jayway.jsonpath.JsonPath;
import com.rentbook.Flows;
import com.rentbook.IntegrationTest;
import com.rentbook.ledger.LedgerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The deposit at move-out: proposed by the landlord, agreed by the tenant, refunded and recorded. */
class DepositSettlementIT extends IntegrationTest {

    @Autowired
    LedgerService ledger;

    private record Tenancy(Flows.Session landlord, Flows.Session tenant, String leaseId) {
        String path(String rest) {
            return "/api/v1/leases/" + leaseId + rest;
        }
    }

    @Test
    void theLandlordProposesTheTenantAsksThenAcceptsAndTheRefundIsRecorded() throws Exception {
        Tenancy home = moveIn();
        payDeposit(home);
        String electricity = addCharge(home, "Electricity for the last month", 180_000);
        endToday(home);

        flows.get(home.landlord(), home.path("/deposit"))
                .andExpect(jsonPath("$.heldPaise").value(2_500_000))
                .andExpect(jsonPath("$.canPropose").value(true))
                .andExpect(jsonPath("$.openCharges[?(@.id == '" + electricity + "')].amountPaise").value(180_000));

        propose(home, """
                [{"description":"Repainting the room","amountPaise":300000},{"chargeId":"%s"}]""".formatted(electricity),
                "Walls needed a fresh coat.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlement.status").value("PROPOSED"))
                .andExpect(jsonPath("$.settlement.deductedPaise").value(480_000))
                .andExpect(jsonPath("$.settlement.refundPaise").value(2_020_000));
        expectEmail(home.tenant(), "Your deposit settlement from Lata Iyer");

        // The tenant reads the same statement and asks about the painting.
        flows.get(home.tenant(), home.path("/deposit")).andExpect(jsonPath("$.settlement.refundPaise").value(2_020_000))
                .andExpect(jsonPath("$.openCharges.length()").value(0));
        tenantPost(home, "/deposit/query", "{\"note\":\"The walls were marked when I moved in.\"}")
                .andExpect(jsonPath("$.settlement.status").value("QUERIED"));
        tenantPost(home, "/deposit/accept", "{}").andExpect(status().isConflict());

        propose(home, """
                [{"description":"Repainting one wall","amountPaise":200000},{"chargeId":"%s"}]""".formatted(electricity), null)
                .andExpect(jsonPath("$.settlement.status").value("PROPOSED"))
                .andExpect(jsonPath("$.settlement.refundPaise").value(2_120_000));

        tenantPost(home, "/deposit/accept", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlement.status").value("ACCEPTED"));

        // The electricity is paid from the deposit, with a receipt that says so.
        flows.get(home.tenant(), home.path("/ledger"))
                .andExpect(jsonPath("$.entries[?(@.description == 'Electricity for the last month')].status").value("PAID"));
        flows.get(home.tenant(), home.path("/receipts")).andExpect(jsonPath("$[0].method").value("DEPOSIT"));

        refund(home, "UPI", ledger.today(), "UPI ref 88120")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlement.status").value("SETTLED"))
                .andExpect(jsonPath("$.settlement.refund.method").value("UPI"));
        expectEmail(home.tenant(), "Your deposit refund of ₹21,200");
        expectEmail(home.landlord(), "Asha Rao accepted the deposit settlement");
        flows.get(home.tenant(), "/api/v1/dashboard/tenant")
                .andExpect(jsonPath("$.lease").doesNotExist())
                .andExpect(jsonPath("$.deposit.settlement.status").value("SETTLED"))
                .andExpect(jsonPath("$.movedOutOf.id").value(home.leaseId()));
        flows.get(home.landlord(), "/api/v1/dashboard/landlord").andExpect(jsonPath("$.deposits.length()").value(0));
    }

    @Test
    void itWaitsForTheLastDayAndEachSideDoesOnlyItsOwnPart() throws Exception {
        Tenancy home = moveIn();
        payDeposit(home);
        propose(home, "[]", null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("lease_active"));

        endToday(home);
        flows.get(home.landlord(), "/api/v1/dashboard/landlord")
                .andExpect(jsonPath("$.deposits[0].leaseId").value(home.leaseId()))
                .andExpect(jsonPath("$.deposits[0].status").doesNotExist());

        propose(home, "[{\"description\":\"Everything\",\"amountPaise\":2600000}]", null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("deductions_exceed_deposit"));
        propose(home, "[{\"description\":\"\",\"amountPaise\":1000}]", null).andExpect(status().isBadRequest());
        flows.authed(MockMvcRequestBuilders.put(home.path("/deposit")), home.tenant(), "{\"deductions\":[]}")
                .andExpect(status().isForbidden());
        refund(home, "CASH", ledger.today(), null).andExpect(status().isNotFound());

        propose(home, "[]", null).andExpect(jsonPath("$.settlement.refundPaise").value(2_500_000));
        flows.authed(post(home.path("/deposit/accept")), home.landlord(), "{}").andExpect(status().isForbidden());
        refund(home, "CASH", ledger.today(), null).andExpect(status().isConflict());

        Tenancy stranger = moveIn();
        flows.get(stranger.landlord(), home.path("/deposit")).andExpect(status().isNotFound());
        flows.get(stranger.tenant(), home.path("/deposit")).andExpect(status().isNotFound());

        // The deposit is never paid off by hand.
        String someCharge = addCharge(stranger, "Water", 50_000);
        flows.authed(post(stranger.path("/payments")), stranger.landlord(), """
                {"chargeIds":["%s"],"method":"DEPOSIT","receivedOn":"%s"}""".formatted(someCharge, ledger.today()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("wrong_method"));
    }

    @Test
    void whenDeductionsTakeTheWholeDepositAcceptingSettlesIt() throws Exception {
        Tenancy home = movedOut();
        propose(home, "[{\"description\":\"Broken bed frame\",\"amountPaise\":2500000}]", null)
                .andExpect(jsonPath("$.settlement.refundPaise").value(0));
        tenantPost(home, "/deposit/accept", "{}").andExpect(jsonPath("$.settlement.status").value("SETTLED"));
    }

    private Tenancy movedOut() throws Exception {
        Tenancy home = moveIn();
        payDeposit(home);
        endToday(home);
        return home;
    }

    private Tenancy moveIn() throws Exception {
        Flows.Session landlord = flows.registerLandlord(Flows.email("lata"));
        String pg = flows.createProperty(landlord, "Deposit PG");
        String flat = flows.addUnit(landlord, pg, "FLAT", "Flat 5C", null);
        Flows.Session tenant = flows.acceptAsNewTenant(flows.inviteToken(landlord, flat, Flows.email("asha"),
                ledger.today()));
        return new Tenancy(landlord, tenant, flows.leaseIdFor(tenant));
    }

    private void payDeposit(Tenancy home) throws Exception {
        String body = flows.get(home.landlord(), home.path("/ledger")).andReturn().getResponse().getContentAsString();
        List<String> deposit = JsonPath.read(body, "$.entries[?(@.kind == 'DEPOSIT')].id");
        flows.authed(post(home.path("/payments")), home.landlord(), """
                {"chargeIds":["%s"],"method":"CASH","receivedOn":"%s"}""".formatted(deposit.getFirst(), ledger.today()))
                .andExpect(status().isCreated());
    }

    private void endToday(Tenancy home) throws Exception {
        flows.authed(post(home.path("/end")), home.landlord(), "{\"endsOn\":\"" + ledger.today() + "\"}")
                .andExpect(status().isOk());
    }

    private String addCharge(Tenancy home, String description, long amountPaise) throws Exception {
        String body = flows.authed(post(home.path("/charges")), home.landlord(), """
                        {"kind":"UTILITY","description":"%s","amountPaise":%d,"dueOn":"%s"}"""
                        .formatted(description, amountPaise, ledger.today()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions propose(Tenancy home, String deductions, String note) throws Exception {
        return flows.authed(MockMvcRequestBuilders.put(home.path("/deposit")), home.landlord(),
                "{\"deductions\":" + deductions + ",\"note\":" + (note == null ? "null" : "\"" + note + "\"") + "}");
    }

    private ResultActions tenantPost(Tenancy home, String rest, String json) throws Exception {
        return flows.authed(post(home.path(rest)), home.tenant(), json);
    }

    private ResultActions refund(Tenancy home, String method, LocalDate on, String reference) throws Exception {
        return flows.authed(post(home.path("/deposit/refund")), home.landlord(), """
                {"method":"%s","refundedOn":"%s","reference":%s}"""
                .formatted(method, on, reference == null ? "null" : "\"" + reference + "\""));
    }

    /** Emails go out after commit on another thread, so wait briefly for the one expected. */
    private void expectEmail(Flows.Session who, String subject) throws Exception {
        String me = flows.get(who, "/api/v1/me").andReturn().getResponse().getContentAsString();
        String address = JsonPath.read(me, "$.email");
        for (int attempt = 0; attempt < 50; attempt++) {
            boolean arrived = org.mockito.Mockito.mockingDetails(mail).getInvocations().stream()
                    .flatMap(invocation -> java.util.Arrays.stream(invocation.getArguments()))
                    .filter(SimpleMailMessage.class::isInstance).map(SimpleMailMessage.class::cast)
                    .anyMatch(message -> List.of(message.getTo()).contains(address) && subject.equals(message.getSubject()));
            if (arrived) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("No email \"" + subject + "\" to " + address);
    }
}
