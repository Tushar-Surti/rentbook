package com.rentbook.condition;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.jayway.jsonpath.JsonPath;
import com.rentbook.Flows;
import com.rentbook.IntegrationTest;
import com.rentbook.ledger.LedgerService;
import net.minidev.json.JSONArray;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Arrays;


import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.head;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Move-in and move-out condition reports: the landlord writes one, the tenant reads the same report, adds notes
 * and confirms it, and the move-out report says what got worse since move-in.
 */
class ConditionReportIT extends IntegrationTest {

    private static final WireMockServer S3 = new WireMockServer(options().dynamicPort());

    static {
        S3.start();
    }

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("rentbook.storage.endpoint", S3::baseUrl);
        registry.add("rentbook.storage.region", () -> "us-east-1");
        registry.add("rentbook.storage.bucket", () -> "rentbook-vault");
        registry.add("rentbook.storage.access-key", () -> "test-access");
        registry.add("rentbook.storage.secret-key", () -> "test-secret");
        registry.add("rentbook.storage.path-style", () -> "true");
    }

    @AfterAll
    static void stopStorage() {
        S3.stop();
    }

    @Autowired
    LedgerService ledger;

    private record Tenancy(Flows.Session landlord, Flows.Session tenant, String leaseId) {
        String reports() {
            return "/api/v1/leases/" + leaseId + "/condition-reports";
        }
    }

    @BeforeEach
    void everythingArrives() {
        S3.resetAll();
        S3.stubFor(head(urlPathMatching("/rentbook-vault/leases/.*")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Length", "2048").withHeader("Content-Type", "image/jpeg")
                .withHeader("ETag", "\"etag\"")));
        S3.stubFor(delete(urlPathMatching("/rentbook-vault/leases/.*")).willReturn(aResponse().withStatus(204)));
    }

    @Test
    void theLandlordWritesTheMoveInReportAndTheTenantConfirmsIt() throws Exception {
        Tenancy home = moveIn();
        Tenancy stranger = moveIn();

        String report = body(flows.authed(post(home.reports()), home.landlord(), "{\"kind\":\"MOVE_IN\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.lines", hasSize(20)))
                .andExpect(jsonPath("$.lines[0].area").value("Entrance"))
                .andExpect(jsonPath("$.lines[0].condition").value("GOOD")));
        String reportId = JsonPath.read(report, "$.id");
        String path = "/api/v1/condition-reports/" + reportId;
        String geyser = line(report, "Geyser");
        String balcony = line(report, "Floor and railing");

        // A draft is the landlord's alone.
        flows.get(home.tenant(), home.reports()).andExpect(jsonPath("$", empty()));
        flows.get(home.tenant(), path).andExpect(status().isNotFound());
        flows.get(stranger.landlord(), path).andExpect(status().isNotFound());
        flows.authed(post(home.reports()), home.landlord(), "{\"kind\":\"MOVE_IN\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("report_exists"));

        flows.authed(patch("/api/v1/condition-lines/" + geyser), home.landlord(),
                        "{\"condition\":\"WORN\",\"note\":\"Knob is loose\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.condition").value("WORN"))
                .andExpect(jsonPath("$.note").value("Knob is loose"));
        flows.authed(post(path + "/lines"), home.landlord(), "{\"area\":\"Kitchen\",\"item\":\"Fridge\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.lines", hasSize(21)))
                .andExpect(jsonPath("$.lines[20].item").value("Fridge"));
        flows.authed(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/api/v1/condition-lines/" + balcony), home.landlord(), "{}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.lines", hasSize(20)));
        String landlordPhoto = photo(home.landlord(), geyser);

        flows.authed(post(path + "/send"), home.landlord(), "{}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SENT"));
        awaitEmail("Check the move-in report for Flat 3A");
        flows.authed(patch("/api/v1/condition-lines/" + geyser), home.landlord(), "{\"condition\":\"GOOD\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("report_sent"));
        photoUpload(home.landlord(), geyser).andExpect(status().isConflict());

        // The tenant reads the same report, adds a photo of their own and a note, and confirms it.
        flows.get(home.tenant(), path).andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[?(@.id == '%s')].photos[0].byTenant".formatted(geyser), hasItem(false)));
        String tenantPhoto = photo(home.tenant(), geyser);
        flows.authed(post(path + "/confirm"), home.tenant(), """
                        {"notes":[{"lineId":"%s","note":"The geyser takes 20 minutes to heat"}]}""".formatted(geyser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.lines[?(@.id == '%s')].tenantNote".formatted(geyser),
                        hasItem("The geyser takes 20 minutes to heat")))
                .andExpect(jsonPath("$.lines[?(@.id == '%s')].photos[*].id".formatted(geyser), hasItem(tenantPhoto)));
        awaitEmail("Asha Rao confirmed the move-in report");

        // From here it stays as it was, and its photos never land on the document shelf.
        flows.authed(post(path + "/confirm"), home.tenant(), "{\"notes\":[]}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("report_confirmed"));
        photoUpload(home.tenant(), geyser).andExpect(status().isConflict());
        flows.authed(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/api/v1/condition-photos/" + tenantPhoto), home.tenant(), "{}")
                .andExpect(status().isConflict());
        flows.authed(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/api/v1/documents/" + landlordPhoto), home.landlord(), "{}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("in_report"));
        flows.get(home.tenant(), "/api/v1/leases/" + home.leaseId() + "/documents")
                .andExpect(jsonPath("$[*].id", not(hasItem(landlordPhoto))));
        flows.get(home.tenant(), home.reports()).andExpect(jsonPath("$[0].status").value("CONFIRMED"));
    }

    @Test
    void theMoveOutReportStartsFromMoveInAndSaysWhatGotWorse() throws Exception {
        Tenancy home = moveIn();
        String moveIn = JsonPath.read(body(flows.authed(post(home.reports()), home.landlord(), "{\"kind\":\"MOVE_IN\"}")),
                "$.id");
        flows.authed(post("/api/v1/condition-reports/" + moveIn + "/send"), home.landlord(), "{}")
                .andExpect(status().isOk());

        flows.authed(post(home.reports()), home.landlord(), "{\"kind\":\"MOVE_OUT\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("not_moving_out"));
        flows.authed(post("/api/v1/leases/" + home.leaseId() + "/end"), home.landlord(),
                "{\"endsOn\":\"" + ledger.today().plusDays(10) + "\"}").andExpect(status().isOk());

        String report = body(flows.authed(post(home.reports()), home.landlord(), "{\"kind\":\"MOVE_OUT\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.comparedWithMoveIn").value(true))
                .andExpect(jsonPath("$.lines", hasSize(20)))
                .andExpect(jsonPath("$.worse").value(0)));
        String walls = ((JSONArray) JsonPath.read(report,
                "$.lines[?(@.area == 'Bedroom' && @.item == 'Walls and paint')].id")).getFirst().toString();

        flows.authed(patch("/api/v1/condition-lines/" + walls), home.landlord(),
                        "{\"condition\":\"DAMAGED\",\"note\":\"Crayon on the wall behind the bed\"}")
                .andExpect(jsonPath("$.atMoveIn").value("GOOD"))
                .andExpect(jsonPath("$.worse").value(true));
        flows.get(home.landlord(), home.reports())
                .andExpect(jsonPath("$[?(@.kind == 'MOVE_OUT')].worse", hasItem(1)));

        // An unsent move-out report can be thrown away; the tenant never saw it.
        String moveOut = JsonPath.read(report, "$.id");
        flows.get(home.tenant(), home.reports()).andExpect(jsonPath("$", hasSize(1)));
        flows.authed(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                "/api/v1/condition-reports/" + moveOut), home.landlord(), "{}").andExpect(status().isNoContent());
        flows.get(home.landlord(), home.reports()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].kind").value("MOVE_IN"));
    }

    @Test
    void aBedStartsWithABedsLines() throws Exception {
        Flows.Session landlord = flows.registerLandlord(Flows.email("lata"));
        String pg = flows.createProperty(landlord, "Report PG");
        String room = flows.addUnit(landlord, pg, "ROOM", "Room 1", null);
        String bed = flows.addUnit(landlord, pg, "BED", "Bed A", room);
        Flows.Session tenant = flows.acceptAsNewTenant(flows.inviteToken(landlord, bed, Flows.email("asha"), ledger.today()));
        String leaseId = flows.leaseIdFor(tenant);
        flows.authed(post("/api/v1/leases/" + leaseId + "/condition-reports"), landlord, "{\"kind\":\"MOVE_IN\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines", hasSize(7)))
                .andExpect(jsonPath("$.lines[0].item").value("Bed and mattress"));
    }

    private Tenancy moveIn() throws Exception {
        Flows.Session landlord = flows.registerLandlord(Flows.email("lata"));
        String pg = flows.createProperty(landlord, "Report PG");
        String flat = flows.addUnit(landlord, pg, "FLAT", "Flat 3A", null);
        Flows.Session tenant = flows.acceptAsNewTenant(
                flows.inviteToken(landlord, flat, Flows.email("asha"), ledger.today()));
        return new Tenancy(landlord, tenant, flows.leaseIdFor(tenant));
    }

    /** Uploads and confirms a photo for a report line, as the browser does, and returns its id. */
    private String photo(Flows.Session who, String lineId) throws Exception {
        String ticket = body(photoUpload(who, lineId).andExpect(status().isCreated()));
        String id = JsonPath.read(ticket, "$.documentId");
        flows.authed(post("/api/v1/documents/" + id + "/complete"), who, "{}").andExpect(status().isOk());
        return id;
    }

    private ResultActions photoUpload(Flows.Session who, String lineId) throws Exception {
        return flows.authed(post("/api/v1/condition-lines/" + lineId + "/photos"), who,
                "{\"filename\":\"geyser.jpg\",\"contentType\":\"image/jpeg\",\"sizeBytes\":2048}");
    }

    /** Waits for the email with this subject; notifications are sent after the commit, off the request thread. */
    private void awaitEmail(String subject) throws InterruptedException {
        for (int attempt = 0; attempt < 50; attempt++) {
            boolean sent = mockingDetails(mail).getInvocations().stream()
                    .flatMap(invocation -> Arrays.stream(invocation.getArguments()))
                    .filter(SimpleMailMessage.class::isInstance)
                    .map(SimpleMailMessage.class::cast)
                    .anyMatch(message -> subject.equals(message.getSubject()));
            if (sent) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("No email went out with the subject \"" + subject + "\"");
    }

    private static String line(String report, String item) {
        return ((JSONArray) JsonPath.read(report, "$.lines[?(@.item == '%s')].id".formatted(item))).getFirst().toString();
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }
}
