package com.rentbook.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.rentbook.auth.AuthService;
import com.rentbook.auth.SessionCookies;
import com.rentbook.auth.Sessions;
import com.rentbook.auth.TokenService;
import com.rentbook.dashboard.DashboardService;
import com.rentbook.invite.InviteService;
import com.rentbook.lease.LeaseService;
import com.rentbook.ledger.LedgerService;
import com.rentbook.property.PropertyRepository;
import com.rentbook.property.PropertyService;
import com.rentbook.property.UnitRepository;
import com.rentbook.user.Role;
import com.rentbook.user.User;
import com.rentbook.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may call what, through the real JWT decoder and role converter. Services are mocked: this test
 * is about the gate, not what is behind it. Runs without a database.
 */
@WebMvcTest(properties = "rentbook.jwt.secret=test-secret-for-the-security-matrix-0123456789")
@Import({SecurityConfig.class, AppConfig.class})
class SecurityMatrixTest {

    private static final String INVITE_BODY = """
            {"tenantName":"Asha","email":"asha@example.in","rentPaise":1250000,"depositPaise":0,
             "dueDay":5,"startsOn":"2026-10-01"}""";

    private static final String CHARGE_BODY = """
            {"kind":"UTILITY","description":"Electricity","amountPaise":180000,"dueOn":"2026-10-10"}""";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    RentbookProperties properties;

    @MockitoBean
    AuthService authService;
    @MockitoBean
    Sessions sessions;
    @MockitoBean
    SessionCookies sessionCookies;
    @MockitoBean
    PropertyService propertyService;
    @MockitoBean
    LeaseService leaseService;
    @MockitoBean
    LedgerService ledgerService;
    @MockitoBean
    com.rentbook.payment.CheckoutService checkoutService;
    @MockitoBean
    com.rentbook.payment.PayoutService payoutService;
    @MockitoBean
    com.rentbook.payment.WebhookService webhookService;
    @MockitoBean
    com.rentbook.payment.ReceiptService receiptService;
    @MockitoBean
    com.rentbook.payment.RecordedPayments recordedPayments;
    @MockitoBean
    com.rentbook.payment.DepositSettlements depositSettlements;
    @MockitoBean
    com.rentbook.caretaker.Caretakers caretakers;
    @MockitoBean
    com.rentbook.caretaker.CaretakerWork caretakerWork;
    @MockitoBean
    com.rentbook.listing.Listings listings;
    @MockitoBean
    com.rentbook.condition.ConditionReports conditionReports;
    @MockitoBean
    com.rentbook.maintenance.TicketService ticketService;
    @MockitoBean
    com.rentbook.document.DocumentService documentService;
    @MockitoBean
    InviteService inviteService;
    @MockitoBean
    DashboardService dashboardService;
    @MockitoBean
    UserRepository userRepository;
    @MockitoBean
    UnitRepository unitRepository;
    @MockitoBean
    PropertyRepository propertyRepository;

    @Test
    void protectedEndpointsNeedAToken() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/properties")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/leases")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/leases/" + UUID.randomUUID() + "/ledger")).andExpect(status().isUnauthorized());
    }

    @Test
    void tenantsCannotReachLandlordEndpoints() throws Exception {
        String tenant = bearer(Role.TENANT);
        UUID id = UUID.randomUUID();
        expectForbidden(get("/api/v1/properties"), tenant);
        expectForbidden(post("/api/v1/properties").contentType(MediaType.APPLICATION_JSON).content("{}"), tenant);
        expectForbidden(post("/api/v1/units/" + id + "/invites").contentType(MediaType.APPLICATION_JSON)
                .content(INVITE_BODY), tenant);
        expectForbidden(get("/api/v1/invites"), tenant);
        expectForbidden(post("/api/v1/invites/" + id + "/revoke"), tenant);
        expectForbidden(post("/api/v1/leases/" + id + "/end").contentType(MediaType.APPLICATION_JSON)
                .content("{\"endsOn\":\"2026-12-01\"}"), tenant);
        expectForbidden(post("/api/v1/leases/" + id + "/charges").contentType(MediaType.APPLICATION_JSON)
                .content(CHARGE_BODY), tenant);
        expectForbidden(post("/api/v1/charges/" + id + "/waive"), tenant);
        // A tenant can't mark their own rent paid.
        expectForbidden(post("/api/v1/leases/" + id + "/payments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"chargeIds\":[\"" + id + "\"],\"method\":\"CASH\",\"receivedOn\":\"2026-09-01\"}"), tenant);
        expectForbidden(get("/api/v1/dashboard/landlord"), tenant);
        // The landlord writes and sends a condition report; the tenant only confirms it.
        expectForbidden(post("/api/v1/leases/" + id + "/condition-reports").contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"MOVE_IN\"}"), tenant);
        expectForbidden(post("/api/v1/condition-reports/" + id + "/send"), tenant);
        expectForbidden(patch("/api/v1/condition-lines/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"condition\":\"GOOD\"}"), tenant);
        expectForbidden(delete("/api/v1/condition-reports/" + id), tenant);
    }

    @Test
    void onlyTheTenantConfirmsAConditionReport() throws Exception {
        expectForbidden(post("/api/v1/condition-reports/" + UUID.randomUUID() + "/confirm")
                .contentType(MediaType.APPLICATION_JSON).content("{\"notes\":[]}"), bearer(Role.LANDLORD));
    }

    @Test
    void caretakersReachOnlyTheirOwnDoor() throws Exception {
        String caretaker = bearer(Role.CARETAKER);
        String id = UUID.randomUUID().toString();
        // Everything a caretaker must not do is closed at the URL, before any service runs.
        expectForbidden(get("/api/v1/properties"), caretaker);
        expectForbidden(get("/api/v1/dashboard/landlord"), caretaker);
        expectForbidden(get("/api/v1/dashboard/tenant"), caretaker);
        expectForbidden(get("/api/v1/payouts/account"), caretaker);
        expectForbidden(get("/api/v1/caretakers"), caretaker);
        expectForbidden(post("/api/v1/charges/" + id + "/waive"), caretaker);
        expectForbidden(post("/api/v1/leases/" + id + "/end").contentType(MediaType.APPLICATION_JSON)
                .content("{\"endsOn\":\"2026-12-01\"}"), caretaker);
        expectForbidden(post("/api/v1/leases/" + id + "/deposit/refund").contentType(MediaType.APPLICATION_JSON)
                .content("{}"), caretaker);
        // And their door is closed to everyone else.
        expectForbidden(get("/api/v1/caretaker/board"), bearer(Role.LANDLORD));
        expectForbidden(get("/api/v1/caretaker/board"), bearer(Role.TENANT));
        expectForbidden(get("/api/v1/caretakers"), bearer(Role.TENANT));
    }

    @Test
    void landlordsCannotReachTenantEndpoints() throws Exception {
        expectForbidden(get("/api/v1/dashboard/tenant"), bearer(Role.LANDLORD));
    }

    @Test
    void eachRoleReachesItsOwnEndpoints() throws Exception {
        mvc.perform(get("/api/v1/properties").header("Authorization", bearer(Role.LANDLORD)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/dashboard/tenant").header("Authorization", bearer(Role.TENANT)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/leases").header("Authorization", bearer(Role.TENANT)))
                .andExpect(status().isOk());
        // Both parties read the ledger; which leases they may read is the service's job.
        mvc.perform(get("/api/v1/leases/" + UUID.randomUUID() + "/ledger").header("Authorization", bearer(Role.TENANT)))
                .andExpect(status().isOk());
    }

    @Test
    void invitePreviewAndLoginArePublic() throws Exception {
        mvc.perform(get("/api/v1/invites/some-invite-token")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void tokensSignedWithAnotherKeyAreRejected() throws Exception {
        JwtEncoder forger = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(
                "a-completely-different-signing-secret-0123456789".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        String forged = new TokenService(forger, properties, Clock.systemUTC()).issue(user(Role.LANDLORD)).value();
        mvc.perform(get("/api/v1/properties").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokensAreRejected() throws Exception {
        Clock anHourAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC);
        mvc.perform(get("/api/v1/properties").header("Authorization", bearer(Role.LANDLORD, properties, anHourAgo)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokensFromAnotherIssuerAreRejected() throws Exception {
        RentbookProperties.Jwt jwt = properties.jwt();
        RentbookProperties otherIssuer = new RentbookProperties(properties.appBaseUrl(), properties.cors(),
                new RentbookProperties.Jwt("someone-else", jwt.secret(), jwt.accessTokenTtl(), jwt.refreshTokenTtl()),
                properties.auth(), properties.invites(), properties.mail(), properties.platformFeeBps());
        mvc.perform(get("/api/v1/properties")
                        .header("Authorization", bearer(Role.LANDLORD, otherIssuer, Clock.systemUTC())))
                .andExpect(status().isUnauthorized());
    }

    /** CORS turns a foreign browser origin away before the controller's own Origin check is reached. */
    @Test
    void refreshRefusesForeignOrigins() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh").header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden());
    }

    private void expectForbidden(MockHttpServletRequestBuilder request, String bearer) throws Exception {
        mvc.perform(request.header("Authorization", bearer)).andExpect(status().isForbidden());
    }

    private String bearer(Role role) {
        return bearer(role, properties, Clock.systemUTC());
    }

    private String bearer(Role role, RentbookProperties props, Clock clock) {
        return "Bearer " + new TokenService(jwtEncoder, props, clock).issue(user(role)).value();
    }

    private static User user(Role role) {
        String name = role.name().toLowerCase();
        return new User(role, name + " person", name + "@example.in", null, "{noop}unused");
    }
}
