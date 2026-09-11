package com.rentbook.maintenance;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.jayway.jsonpath.JsonPath;
import com.rentbook.Flows;
import com.rentbook.IntegrationTest;
import com.rentbook.ledger.LedgerService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.head;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Maintenance end to end: one thread both parties write on, status moves by who may make them, and
 * photos that go straight to storage (a stand-in S3 here) and are checked there before they're attached.
 */
class MaintenanceFlowIT extends IntegrationTest {

    private static final WireMockServer S3 = new WireMockServer(options().dynamicPort());

    static {
        S3.start();
    }

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("rentbook.storage.endpoint", S3::baseUrl);
        registry.add("rentbook.storage.region", () -> "us-east-1");
        registry.add("rentbook.storage.bucket", () -> "rentbook-test");
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

    private record Tenancy(Flows.Session landlord, Flows.Session tenant, String leaseId, String propertyId) {
    }

    @BeforeEach
    void emptyBucket() {
        S3.resetAll();
    }

    @Test
    void aTenantOpensARequestAndBothPartiesWriteOnOneThread() throws Exception {
        Tenancy home = moveIn();
        String ticketId = open(home, "Kitchen tap leaking", "URGENT", "It drips all night.", List.of());

        flows.get(home.landlord(), "/api/v1/tickets?open=true&propertyId=" + home.propertyId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(ticketId)))
                .andExpect(jsonPath("$[0].tenant.fullName").value("Asha Rao"))
                .andExpect(jsonPath("$[0].priority").value("URGENT"));
        flows.authed(post("/api/v1/tickets/" + ticketId + "/events"), home.landlord(),
                        "{\"body\":\"The plumber comes at 10 tomorrow.\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author.name").value("Lata Iyer"));

        flows.get(home.tenant(), "/api/v1/tickets/" + ticketId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(2))
                .andExpect(jsonPath("$.events[0].body").value("It drips all night."))
                .andExpect(jsonPath("$.events[1].body").value("The plumber comes at 10 tomorrow."))
                .andExpect(jsonPath("$.nextStatuses[0]").value("CLOSED"));
        flows.get(home.landlord(), "/api/v1/tickets/" + ticketId)
                .andExpect(jsonPath("$.nextStatuses.length()").value(3));
    }

    @Test
    void theLandlordMovesARequestForwardAndOnlyTheTenantClosesOrReopensIt() throws Exception {
        Tenancy home = moveIn();
        String ticketId = open(home, "Fan not working", "NORMAL", "The ceiling fan won't start.", List.of());

        move(home.landlord(), ticketId, "ACKNOWLEDGED").andExpect(status().isOk());
        move(home.tenant(), ticketId, "IN_PROGRESS").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("status_change"));
        move(home.landlord(), ticketId, "IN_PROGRESS").andExpect(status().isOk());
        move(home.landlord(), ticketId, "RESOLVED").andExpect(status().isOk());
        move(home.landlord(), ticketId, "CLOSED").andExpect(status().isConflict());
        move(home.tenant(), ticketId, "CLOSED").andExpect(status().isOk());
        flows.authed(post("/api/v1/tickets/" + ticketId + "/events"), home.landlord(), "{\"body\":\"One more thing\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ticket_closed"));
        move(home.tenant(), ticketId, "OPEN").andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket.status").value("OPEN"))
                .andExpect(jsonPath("$.events[?(@.kind == 'STATUS_CHANGE')].toStatus",
                        org.hamcrest.Matchers.contains("ACKNOWLEDGED", "IN_PROGRESS", "RESOLVED", "CLOSED", "OPEN")));
    }

    @Test
    void strangersFindNothing() throws Exception {
        Tenancy home = moveIn();
        Tenancy other = moveIn();
        String ticketId = open(home, "Door lock stiff", "LOW", "The lock sticks.", List.of());

        flows.get(other.tenant(), "/api/v1/tickets/" + ticketId).andExpect(status().isNotFound());
        flows.get(other.landlord(), "/api/v1/tickets/" + ticketId).andExpect(status().isNotFound());
        flows.authed(post("/api/v1/tickets/" + ticketId + "/events"), other.tenant(), "{\"body\":\"hi\"}")
                .andExpect(status().isNotFound());
        move(other.landlord(), ticketId, "RESOLVED").andExpect(status().isNotFound());
        flows.authed(post("/api/v1/tickets"), other.tenant(), openBody(home.leaseId(), "Mine now", "LOW", "x", List.of()))
                .andExpect(status().isNotFound());
        flows.authed(post("/api/v1/tickets"), home.landlord(), openBody(home.leaseId(), "By landlord", "LOW", "x", List.of()))
                .andExpect(status().isForbidden());
    }

    @Test
    void photosGoStraightToStorageAndAreCheckedThereBeforeTheyAttach() throws Exception {
        Tenancy home = moveIn();
        String upload = flows.authed(post("/api/v1/uploads"), home.tenant(), """
                        {"leaseId":"%s","type":"TICKET_PHOTO","filename":"leak.jpg","contentType":"image/jpeg","sizeBytes":123456}
                        """.formatted(home.leaseId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.method").value("PUT"))
                .andExpect(jsonPath("$.url", containsString("/rentbook-test/leases/" + home.leaseId() + "/")))
                .andExpect(jsonPath("$.url", containsString("X-Amz-Signature=")))
                .andExpect(jsonPath("$.headers.content-type").value("image/jpeg"))
                .andReturn().getResponse().getContentAsString();
        String photoId = JsonPath.read(upload, "$.documentId");

        // Nothing in the bucket yet: the browser's word that it finished is not enough.
        S3.stubFor(head(urlPathMatching("/rentbook-test/leases/.*")).willReturn(aResponse().withStatus(404)));
        complete(home.tenant(), photoId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("not_uploaded"));
        flows.authed(post("/api/v1/tickets"), home.tenant(),
                        openBody(home.leaseId(), "Damp wall", "NORMAL", "See photo.", List.of(photoId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("photos"));

        S3.stubFor(head(urlPathMatching("/rentbook-test/leases/.*")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Length", "123456").withHeader("Content-Type", "image/jpeg")
                .withHeader("ETag", "\"etag\"")));
        complete(home.tenant(), photoId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AVAILABLE"));

        String ticketId = open(home, "Damp wall", "NORMAL", "See photo.", List.of(photoId));
        flows.get(home.landlord(), "/api/v1/tickets/" + ticketId)
                .andExpect(jsonPath("$.events[0].photos[0].id").value(photoId))
                .andExpect(jsonPath("$.events[0].photos[0].url", containsString("X-Amz-Signature=")))
                .andExpect(jsonPath("$.events[0].photos[0].url", containsString("response-content-disposition=inline")));
        flows.get(home.landlord(), "/api/v1/documents/" + photoId + "/download").andExpect(status().isOk());

        // A photo belongs to one message; and strangers can't fetch it.
        flows.authed(post("/api/v1/tickets/" + ticketId + "/events"), home.tenant(),
                        "{\"body\":\"Again\",\"photoIds\":[\"%s\"]}".formatted(photoId))
                .andExpect(status().isBadRequest());
        Tenancy other = moveIn();
        flows.get(other.tenant(), "/api/v1/documents/" + photoId + "/download").andExpect(status().isNotFound());
        flows.get(other.landlord(), "/api/v1/documents/" + photoId + "/download").andExpect(status().isNotFound());
    }

    @Test
    void uploadsRefuseWhatStorageShouldNotHold() throws Exception {
        Tenancy home = moveIn();
        upload(home, "TICKET_PHOTO", "page.html", "text/html", 2_000).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("file_type"));
        upload(home, "TICKET_PHOTO", "huge.jpg", "image/jpeg", 20L * 1024 * 1024).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("file_size"));
        upload(home, "TICKET_PHOTO", "scan.pdf", "application/pdf", 2_000).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("file_type"));
        upload(home, "RECEIPT", "receipt.pdf", "application/pdf", 2_000).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("document_type"));
    }

    private Tenancy moveIn() throws Exception {
        Flows.Session landlord = flows.registerLandlord(Flows.email("lata"));
        String pg = flows.createProperty(landlord, "Repair PG");
        String flat = flows.addUnit(landlord, pg, "FLAT", "Flat 1B", null);
        Flows.Session tenant = flows.acceptAsNewTenant(
                flows.inviteToken(landlord, flat, Flows.email("asha"), ledger.today()));
        return new Tenancy(landlord, tenant, flows.leaseIdFor(tenant), pg);
    }

    private String open(Tenancy home, String title, String priority, String body, List<String> photoIds)
            throws Exception {
        String thread = flows.authed(post("/api/v1/tickets"), home.tenant(),
                        openBody(home.leaseId(), title, priority, body, photoIds))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticket.status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(thread, "$.ticket.id");
    }

    private static String openBody(String leaseId, String title, String priority, String body, List<String> photoIds) {
        String photos = photoIds.stream().map(id -> "\"" + id + "\"").reduce((a, b) -> a + "," + b).orElse("");
        return """
                {"leaseId":"%s","title":"%s","category":"PLUMBING","priority":"%s","body":"%s","photoIds":[%s]}
                """.formatted(leaseId, title, priority, body, photos);
    }

    private ResultActions move(Flows.Session who, String ticketId, String to) throws Exception {
        return flows.authed(patch("/api/v1/tickets/" + ticketId + "/status"), who, "{\"status\":\"%s\"}".formatted(to));
    }

    private ResultActions complete(Flows.Session who, String documentId) throws Exception {
        return flows.authed(MockMvcRequestBuilders.post("/api/v1/documents/" + documentId + "/complete"), who, "{}");
    }

    private ResultActions upload(Tenancy home, String type, String filename, String contentType, long size)
            throws Exception {
        return flows.authed(post("/api/v1/uploads"), home.tenant(), """
                {"leaseId":"%s","type":"%s","filename":"%s","contentType":"%s","sizeBytes":%d}
                """.formatted(home.leaseId(), type, filename, contentType, size));
    }
}
