package com.rentbook.document;

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.head;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The document vault: one shelf per lease, what each party may see on it, and who may take things off.
 * Files go to a stand-in S3; the bucket is always checked before a file counts.
 */
class DocumentVaultIT extends IntegrationTest {

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
        String shelf() {
            return "/api/v1/leases/" + leaseId + "/documents";
        }
    }

    @BeforeEach
    void everythingArrives() {
        S3.resetAll();
        S3.stubFor(head(urlPathMatching("/rentbook-vault/leases/.*")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Length", "2048").withHeader("Content-Type", "application/pdf")
                .withHeader("ETag", "\"etag\"")));
        S3.stubFor(delete(urlPathMatching("/rentbook-vault/leases/.*")).willReturn(aResponse().withStatus(204)));
    }

    @Test
    void theAgreementAndTheTenantsIdAreSharedByTheLeasesTwoPartiesOnly() throws Exception {
        Tenancy home = moveIn();
        Tenancy other = moveIn();
        String agreement = file(home.landlord(), home, "LEASE", "agreement.pdf", "application/pdf", null);
        String id = file(home.tenant(), home, "KYC", "aadhaar.pdf", "application/pdf", null);

        flows.get(home.tenant(), home.shelf()).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')].uploadedBy".formatted(agreement), hasItem("Lata Iyer")))
                .andExpect(jsonPath("$[?(@.id == '%s')].mine".formatted(agreement), hasItem(false)))
                .andExpect(jsonPath("$[?(@.id == '%s')].mine".formatted(id), hasItem(true)));
        flows.get(home.landlord(), home.shelf())
                .andExpect(jsonPath("$[*].id", hasItem(id)))
                .andExpect(jsonPath("$[?(@.id == '%s')].type".formatted(id), hasItem("KYC")));

        flows.get(other.tenant(), home.shelf()).andExpect(status().isNotFound());
        flows.get(other.landlord(), home.shelf()).andExpect(status().isNotFound());
        flows.get(other.tenant(), "/api/v1/documents/" + id + "/download").andExpect(status().isNotFound());
        flows.get(home.landlord(), "/api/v1/documents/" + id + "/download?attachment=true")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", containsString("response-content-disposition=attachment")));
    }

    @Test
    void onlyTheLandlordCanKeepAFileToThemselves() throws Exception {
        Tenancy home = moveIn();
        String notes = file(home.landlord(), home, "OTHER", "repairs-quote.pdf", "application/pdf", "LANDLORD_ONLY");
        String tenantsOwn = file(home.tenant(), home, "OTHER", "noc.pdf", "application/pdf", "LANDLORD_ONLY");

        flows.get(home.landlord(), home.shelf())
                .andExpect(jsonPath("$[*].id", hasItem(notes)))
                .andExpect(jsonPath("$[?(@.id == '%s')].visibility".formatted(tenantsOwn), hasItem("LEASE_PARTIES")));
        flows.get(home.tenant(), home.shelf()).andExpect(jsonPath("$[*].id", not(hasItem(notes))));
        flows.get(home.tenant(), "/api/v1/documents/" + notes + "/download").andExpect(status().isNotFound());
    }

    @Test
    void onlyWhoeverFiledADocumentTakesItOffTheShelf() throws Exception {
        Tenancy home = moveIn();
        String agreement = file(home.landlord(), home, "LEASE", "agreement.pdf", "application/pdf", null);

        remove(home.tenant(), agreement).andExpect(status().isNotFound());
        remove(home.landlord(), agreement).andExpect(status().isNoContent());
        flows.get(home.tenant(), home.shelf()).andExpect(jsonPath("$[*].id", not(hasItem(agreement))));
        S3.verify(deleteRequestedFor(urlPathEqualTo("/rentbook-vault/leases/" + home.leaseId() + "/" + agreement + ".pdf")));
    }

    @Test
    void eachPartyFilesOnlyWhatIsTheirsToFile() throws Exception {
        Tenancy home = moveIn();
        upload(home.tenant(), home, "LEASE", "agreement.pdf", "application/pdf", null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("document_type"));
        upload(home.landlord(), home, "KYC", "someone.pdf", "application/pdf", null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("document_type"));
    }

    @Test
    void aPhotoInARequestsThreadStaysThereAndOffTheShelf() throws Exception {
        Tenancy home = moveIn();
        String photo = file(home.tenant(), home, "TICKET_PHOTO", "crack.jpg", "image/jpeg", null);
        flows.authed(post("/api/v1/tickets"), home.tenant(), """
                        {"leaseId":"%s","title":"Crack in the wall","category":"OTHER","priority":"LOW",
                         "body":"Above the window.","photoIds":["%s"]}""".formatted(home.leaseId(), photo))
                .andExpect(status().isCreated());

        flows.get(home.tenant(), home.shelf()).andExpect(jsonPath("$[*].id", not(hasItem(photo))));
        remove(home.tenant(), photo).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("in_thread"));
    }

    @Test
    void uploadsThatWereNeverConfirmedAreClearedAfterADay() throws Exception {
        Tenancy home = moveIn();
        String abandoned = JsonPath.read(upload(home.tenant(), home, "KYC", "left.pdf", "application/pdf", null)
                .andReturn().getResponse().getContentAsString(), "$.documentId");
        String fresh = JsonPath.read(upload(home.tenant(), home, "KYC", "new.pdf", "application/pdf", null)
                .andReturn().getResponse().getContentAsString(), "$.documentId");
        jdbc.update("update documents set created_at = created_at - interval '2 days' where id = ?::uuid", abandoned);

        cleaner.clear();

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForList("select id::text from documents where id in (?::uuid, ?::uuid)",
                String.class, abandoned, fresh)).containsExactly(fresh);
        S3.verify(deleteRequestedFor(urlPathEqualTo("/rentbook-vault/leases/" + home.leaseId() + "/" + abandoned + ".pdf")));
    }

    @Autowired
    AbandonedUploadCleaner cleaner;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    private Tenancy moveIn() throws Exception {
        Flows.Session landlord = flows.registerLandlord(Flows.email("lata"));
        String pg = flows.createProperty(landlord, "Vault PG");
        String flat = flows.addUnit(landlord, pg, "FLAT", "Flat 3A", null);
        Flows.Session tenant = flows.acceptAsNewTenant(
                flows.inviteToken(landlord, flat, Flows.email("asha"), ledger.today()));
        return new Tenancy(landlord, tenant, flows.leaseIdFor(tenant));
    }

    /** Uploads and confirms a file, as the browser does, and returns its id. */
    private String file(Flows.Session who, Tenancy home, String type, String filename, String contentType,
                        String visibility) throws Exception {
        String ticket = upload(who, home, type, filename, contentType, visibility)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(ticket, "$.documentId");
        flows.authed(MockMvcRequestBuilders.post("/api/v1/documents/" + id + "/complete"), who, "{}")
                .andExpect(status().isOk());
        return id;
    }

    private org.springframework.test.web.servlet.ResultActions upload(Flows.Session who, Tenancy home, String type,
                                                                      String filename, String contentType,
                                                                      String visibility) throws Exception {
        String visible = visibility == null ? "null" : "\"" + visibility + "\"";
        return flows.authed(post("/api/v1/uploads"), who, """
                {"leaseId":"%s","type":"%s","filename":"%s","contentType":"%s","sizeBytes":2048,"visibility":%s}
                """.formatted(home.leaseId(), type, filename, contentType, visible));
    }

    private org.springframework.test.web.servlet.ResultActions remove(Flows.Session who, String documentId)
            throws Exception {
        return flows.authed(MockMvcRequestBuilders.delete("/api/v1/documents/" + documentId), who, "{}");
    }
}
