package com.rentbook.listing;

import com.jayway.jsonpath.JsonPath;
import com.rentbook.Flows;
import com.rentbook.IntegrationTest;
import com.rentbook.ledger.LedgerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A vacancy listed publicly, enquired about, and closed by the move-in it leads to. */
class ListingIT extends IntegrationTest {

    @Autowired
    LedgerService ledger;

    @Test
    void aListedFlatTakesEnquiriesAndClosesWhenSomeoneMovesIn() throws Exception {
        Flows.Session landlord = flows.registerLandlord(Flows.email("lata"));
        String pg = flows.createProperty(landlord, "Sunrise PG");
        String flat = flows.addUnit(landlord, pg, "FLAT", "Flat 4A", null);

        String body = list(landlord, flat).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.place.unitLabel").value("Flat 4A"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        String slug = JsonPath.read(body, "$.slug");
        assertThat(slug).hasSize(10);
        list(landlord, flat).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("already_listed"));
        flows.get(landlord, "/api/v1/dashboard/landlord")
                .andExpect(jsonPath("$.properties[0].hooks[0].listingId").value(id));

        // Anyone with the link sees the place and its terms, but not the street address.
        String page = mvc.perform(get("/api/v1/public/listings/" + slug)).andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(true))
                .andExpect(jsonPath("$.landlordFirstName").value("Lata"))
                .andExpect(jsonPath("$.place.propertyName").value("Sunrise PG"))
                .andExpect(jsonPath("$.place.pincode").value("560095"))
                .andExpect(jsonPath("$.rentPaise").value(1_450_000))
                .andReturn().getResponse().getContentAsString();
        assertThat(page).doesNotContain("Koramangala 5th Block");

        enquire(slug, """
                {"name":"Priya Sharma","phone":"+919876500011","email":"priya@example.in",
                 "message":"Is it close to the metro?","visitOn":"%s"}""".formatted(ledger.today().plusDays(2)))
                .andExpect(status().isAccepted());
        // A bot fills the hidden field; it is thanked and ignored.
        enquire(slug, "{\"name\":\"Bot\",\"phone\":\"+911111111111\",\"website\":\"http://spam\"}")
                .andExpect(status().isAccepted());
        enquire(slug, "{\"name\":\"Late\",\"phone\":\"+919876500012\",\"visitOn\":\"2020-01-01\"}")
                .andExpect(status().isBadRequest());
        awaitEmail(landlord, "New enquiry for Flat 4A, Sunrise PG from Priya Sharma");

        String enquiries = flows.get(landlord, "/api/v1/listings/" + id)
                .andExpect(jsonPath("$.enquiries.length()").value(1))
                .andExpect(jsonPath("$.enquiries[0].phone").value("+919876500011"))
                .andReturn().getResponse().getContentAsString();
        String enquiryId = JsonPath.read(enquiries, "$.enquiries[0].id");
        flows.authed(patch("/api/v1/listings/" + id + "/enquiries/" + enquiryId), landlord, "{\"status\":\"INVITED\"}")
                .andExpect(jsonPath("$.enquiries[0].status").value("INVITED"));

        // Priya moves in: the listing closes itself, and the page says the place is let.
        flows.acceptAsNewTenant(flows.inviteToken(landlord, flat, Flows.email("priya"), ledger.today()));
        flows.get(landlord, "/api/v1/listings/" + id).andExpect(jsonPath("$.status").value("CLOSED"));
        mvc.perform(get("/api/v1/public/listings/" + slug)).andExpect(jsonPath("$.open").value(false))
                .andExpect(jsonPath("$.photos.length()").value(0));
        enquire(slug, "{\"name\":\"Too late\",\"phone\":\"+919876500013\"}").andExpect(status().isGone());
    }

    @Test
    void onlyItsLandlordManagesIt() throws Exception {
        Flows.Session landlord = flows.registerLandlord(Flows.email("lata"));
        String pg = flows.createProperty(landlord, "Own PG");
        String flat = flows.addUnit(landlord, pg, "FLAT", "Flat 1", null);
        String occupied = flows.addUnit(landlord, pg, "FLAT", "Flat 2", null);
        Flows.Session tenant = flows.acceptAsNewTenant(flows.inviteToken(landlord, occupied, Flows.email("asha"), ledger.today()));
        list(landlord, occupied).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("unit_not_vacant"));

        String id = JsonPath.read(list(landlord, flat).andReturn().getResponse().getContentAsString(), "$.id");
        Flows.Session other = flows.registerLandlord(Flows.email("other"));
        flows.get(other, "/api/v1/listings/" + id).andExpect(status().isNotFound());
        list(other, flat).andExpect(status().isNotFound());
        flows.get(tenant, "/api/v1/listings").andExpect(status().isForbidden());

        // Photo storage is off in this test setup, so photos are refused plainly rather than failing later.
        flows.authed(post("/api/v1/listings/" + id + "/photos"), landlord, "{\"contentType\":\"image/jpeg\",\"sizeBytes\":2000}")
                .andExpect(status().isServiceUnavailable());

        flows.authed(post("/api/v1/listings/" + id + "/close"), landlord, "{}").andExpect(jsonPath("$.status").value("CLOSED"));
        list(landlord, flat).andExpect(status().isCreated());
    }

    private ResultActions list(Flows.Session landlord, String unitId) throws Exception {
        return flows.authed(post("/api/v1/units/" + unitId + "/listing"), landlord, """
                {"rentPaise":1450000,"depositPaise":2900000,"availableFrom":"%s","description":"Sunny flat, two minutes from the bus stop."}"""
                .formatted(ledger.today()));
    }

    private ResultActions enquire(String slug, String json) throws Exception {
        return mvc.perform(post("/api/v1/public/listings/" + slug + "/enquiries").contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private void awaitEmail(Flows.Session who, String subject) throws Exception {
        String address = JsonPath.read(flows.get(who, "/api/v1/me").andReturn().getResponse().getContentAsString(), "$.email");
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
