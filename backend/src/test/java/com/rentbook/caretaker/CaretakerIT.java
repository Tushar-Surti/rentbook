package com.rentbook.caretaker;

import com.jayway.jsonpath.JsonPath;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.rentbook.Flows;
import com.rentbook.IntegrationTest;
import com.rentbook.ledger.LedgerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A caretaker works on the properties their landlord assigns, in their own name, and nowhere else. */
class CaretakerIT extends IntegrationTest {

    @Autowired
    LedgerService ledger;

    private record Place(String propertyId, String leaseId, Flows.Session tenant) {
    }

    @Test
    void aCaretakerRunsTheirPropertyAndOnlyTheirs() throws Exception {
        Flows.Session landlord = flows.registerLandlord(Flows.email("lata"));
        Place sunrise = let(landlord, "Sunrise PG");
        Place hillside = let(landlord, "Hillside PG");

        // Invited to Sunrise only; the link arrives by email and opens the invite.
        String caretakerEmail = Flows.email("ramesh");
        String body = flows.authed(post("/api/v1/caretakers"), landlord, """
                        {"fullName":"Ramesh Kumar","email":"%s","phone":"+919800011122","propertyIds":["%s"]}"""
                        .formatted(caretakerEmail, sunrise.propertyId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caretaker.status").value("INVITED"))
                .andReturn().getResponse().getContentAsString();
        String link = JsonPath.read(body, "$.link");
        String token = link.substring(link.lastIndexOf('/') + 1);
        awaitEmail(caretakerEmail, "Lata Iyer added you as a caretaker on Rentbook");
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/caretaker-invites/" + token))
                .andExpect(jsonPath("$.landlordName").value("Lata Iyer"))
                .andExpect(jsonPath("$.properties[0].name").value("Sunrise PG"));

        MvcResult joined = mvc.perform(post("/api/v1/caretaker-invites/" + token + "/accept")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + Flows.PASSWORD + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("CARETAKER"))
                .andReturn();
        Flows.Session ramesh = Flows.session(joined);
        mvc.perform(post("/api/v1/caretaker-invites/" + token + "/accept")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + Flows.PASSWORD + "\"}"))
                .andExpect(status().isGone());

        // The register shows Sunrise, not Hillside.
        flows.get(ramesh, "/api/v1/caretaker/board")
                .andExpect(jsonPath("$.landlordName").value("Lata Iyer"))
                .andExpect(jsonPath("$.properties.length()").value(1))
                .andExpect(jsonPath("$.properties[0].name").value("Sunrise PG"));
        flows.get(ramesh, "/api/v1/caretaker/leases/" + sunrise.leaseId()).andExpect(status().isOk());
        flows.get(ramesh, "/api/v1/caretaker/leases/" + hillside.leaseId()).andExpect(status().isNotFound());
        flows.get(ramesh, "/api/v1/caretaker/leases/" + hillside.leaseId() + "/ledger").andExpect(status().isNotFound());
        // The landlord's and tenant's own doors stay shut to them.
        flows.get(ramesh, "/api/v1/leases/" + sunrise.leaseId()).andExpect(status().isNotFound());
        flows.get(ramesh, "/api/v1/leases/" + sunrise.leaseId() + "/ledger").andExpect(status().isNotFound());
        flows.get(ramesh, "/api/v1/leases/" + sunrise.leaseId() + "/documents").andExpect(status().isNotFound());

        // Rent paid to Ramesh in cash: the receipt and the tenant's email say who recorded it.
        String ledgerBody = flows.get(ramesh, "/api/v1/caretaker/leases/" + sunrise.leaseId() + "/ledger")
                .andReturn().getResponse().getContentAsString();
        List<String> rent = JsonPath.read(ledgerBody, "$.entries[?(@.kind == 'RENT')].id");
        flows.authed(post("/api/v1/caretaker/leases/" + sunrise.leaseId() + "/payments"), ramesh, """
                        {"chargeIds":["%s"],"method":"CASH","receivedOn":"%s"}""".formatted(rent.getFirst(), ledger.today()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receiptNumber").value("0001"));
        flows.authed(post("/api/v1/caretaker/leases/" + hillside.leaseId() + "/payments"), ramesh, """
                        {"chargeIds":["%s"],"method":"CASH","receivedOn":"%s"}""".formatted(rent.getFirst(), ledger.today()))
                .andExpect(status().isNotFound());
        String receiptId = JsonPath.read(flows.get(sunrise.tenant(), "/api/v1/leases/" + sunrise.leaseId() + "/receipts")
                .andReturn().getResponse().getContentAsString(), "$[0].id");
        byte[] pdf = flows.get(sunrise.tenant(), "/api/v1/receipts/" + receiptId + "/pdf").andReturn().getResponse()
                .getContentAsByteArray();
        assertThat(new PdfTextExtractor(new PdfReader(pdf)).getTextFromPage(1).replaceAll("\\s+", " "))
                .contains("Recorded by Ramesh Kumar, the landlord's caretaker,");

        // Repairs: the new request reaches Ramesh; his reply and status move are in his own name.
        String ticketId = openRequest(sunrise);
        awaitEmail(caretakerEmail, "New request: Fan not working");
        flows.get(ramesh, "/api/v1/caretaker/tickets?open=true").andExpect(jsonPath("$.length()").value(1));
        flows.authed(post("/api/v1/caretaker/tickets/" + ticketId + "/events"), ramesh,
                "{\"body\":\"I'll bring an electrician at 5.\"}").andExpect(status().isCreated());
        flows.authed(patch("/api/v1/caretaker/tickets/" + ticketId + "/status"), ramesh, "{\"status\":\"IN_PROGRESS\"}")
                .andExpect(jsonPath("$.ticket.status").value("IN_PROGRESS"));
        flows.get(landlord, "/api/v1/tickets/" + ticketId)
                .andExpect(jsonPath("$.events[1].author.name").value("Ramesh Kumar"))
                .andExpect(jsonPath("$.events[1].author.role").value("CARETAKER"));
        String elsewhere = openRequest(hillside);
        flows.get(ramesh, "/api/v1/caretaker/tickets/" + elsewhere).andExpect(status().isNotFound());
        flows.authed(post("/api/v1/caretaker/tickets/" + elsewhere + "/events"), ramesh, "{\"body\":\"hi\"}")
                .andExpect(status().isNotFound());

        // Reassigned to Hillside, access follows at once.
        String caretakerId = JsonPath.read(body, "$.caretaker.id");
        flows.authed(put("/api/v1/caretakers/" + caretakerId + "/properties"), landlord,
                "{\"propertyIds\":[\"" + hillside.propertyId() + "\"]}").andExpect(status().isOk());
        flows.get(ramesh, "/api/v1/caretaker/leases/" + sunrise.leaseId()).andExpect(status().isNotFound());
        flows.get(ramesh, "/api/v1/caretaker/leases/" + hillside.leaseId()).andExpect(status().isOk());

        // Removed: refused everywhere, signed out everywhere, and can't sign back in.
        mvc.perform(delete("/api/v1/caretakers/" + caretakerId).header("Authorization", landlord.bearer()))
                .andExpect(status().isNoContent());
        flows.get(ramesh, "/api/v1/caretaker/board").andExpect(status().isForbidden());
        flows.refresh(ramesh.refreshToken()).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"%s"}""".formatted(caretakerEmail, Flows.PASSWORD)))
                .andExpect(status().isUnauthorized());
        flows.get(landlord, "/api/v1/caretakers").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void onlyTheLandlordsOwnPropertiesAndANewAddressCanBeUsed() throws Exception {
        Flows.Session landlord = flows.registerLandlord(Flows.email("lata"));
        Place own = let(landlord, "Own PG");
        Flows.Session other = flows.registerLandlord(Flows.email("other"));
        Place theirs = let(other, "Their PG");

        flows.authed(post("/api/v1/caretakers"), landlord, """
                {"fullName":"Ramesh","email":"%s","propertyIds":["%s"]}""".formatted(Flows.email("r"), theirs.propertyId()))
                .andExpect(status().isNotFound());
        flows.authed(post("/api/v1/caretakers"), landlord, """
                {"fullName":"Ramesh","email":"%s","propertyIds":[]}""".formatted(Flows.email("r")))
                .andExpect(status().isBadRequest());
        String tenantEmail = JsonPath.read(flows.get(own.tenant(), "/api/v1/me").andReturn().getResponse()
                .getContentAsString(), "$.email");
        flows.authed(post("/api/v1/caretakers"), landlord, """
                {"fullName":"Ramesh","email":"%s","propertyIds":["%s"]}""".formatted(tenantEmail, own.propertyId()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("email_has_account"));
        flows.get(other, "/api/v1/caretakers").andExpect(jsonPath("$.length()").value(0));
    }

    private Place let(Flows.Session landlord, String name) throws Exception {
        String property = flows.createProperty(landlord, name);
        String flat = flows.addUnit(landlord, property, "FLAT", "Flat 1", null);
        Flows.Session tenant = flows.acceptAsNewTenant(flows.inviteToken(landlord, flat, Flows.email("asha"),
                ledger.today()));
        return new Place(property, flows.leaseIdFor(tenant), tenant);
    }

    private String openRequest(Place place) throws Exception {
        String body = flows.authed(post("/api/v1/tickets"), place.tenant(), """
                        {"leaseId":"%s","title":"Fan not working","category":"ELECTRICAL","priority":"NORMAL",
                         "body":"The ceiling fan stopped last night.","photoIds":[]}""".formatted(place.leaseId()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.ticket.id");
    }

    private void awaitEmail(String address, String subject) throws InterruptedException {
        for (int attempt = 0; attempt < 50; attempt++) {
            boolean arrived = org.mockito.Mockito.mockingDetails(mail).getInvocations().stream()
                    .flatMap(invocation -> Arrays.stream(invocation.getArguments()))
                    .filter(SimpleMailMessage.class::isInstance).map(SimpleMailMessage.class::cast)
                    .anyMatch(message -> List.of(message.getTo()).contains(address) && subject.equals(message.getSubject()));
            if (arrived) return;
            Thread.sleep(100);
        }
        throw new AssertionError("No email \"" + subject + "\" to " + address);
    }
}
