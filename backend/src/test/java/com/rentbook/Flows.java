package com.rentbook;

import com.jayway.jsonpath.JsonPath;
import com.rentbook.common.IndiaTime;
import jakarta.servlet.http.Cookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The user journeys the tests are built from, driven through the real HTTP API. */
public final class Flows {

    public static final String PASSWORD = "correct horse battery";

    private final MockMvc mvc;

    Flows(MockMvc mvc) {
        this.mvc = mvc;
    }

    public record Session(String accessToken, String refreshToken, String userId) {
        public String bearer() {
            return "Bearer " + accessToken;
        }
    }

    public static String email(String who) {
        return who + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.in";
    }

    /** A move-in a few weeks out, so tests that don't care about dates never drift into the past. */
    public static LocalDate soon() {
        return LocalDate.now(IndiaTime.ZONE).plusDays(20);
    }

    public Session registerLandlord(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"fullName":"Lata Iyer","email":"%s","phone":"+919876543210","password":"%s"}
                        """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        return session(result);
    }

    public ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("rb_refresh", refreshToken)));
    }

    public String createProperty(Session landlord, String name) throws Exception {
        return id(authed(post("/api/v1/properties"), landlord, """
                {"name":"%s","kind":"PG","addressLine":"14 Koramangala 5th Block","city":"Bengaluru","pincode":"560095"}
                """.formatted(name)).andExpect(status().isCreated()));
    }

    public String addUnit(Session landlord, String propertyId, String kind, String label, String parentUnitId)
            throws Exception {
        String parent = parentUnitId == null ? "null" : "\"" + parentUnitId + "\"";
        return id(authed(post("/api/v1/properties/" + propertyId + "/units"), landlord, """
                {"kind":"%s","label":"%s","parentUnitId":%s,"defaultRentPaise":1250000}
                """.formatted(kind, label, parent)).andExpect(status().isCreated()));
    }

    public ResultActions invite(Session landlord, String unitId, String tenantEmail) throws Exception {
        return invite(landlord, unitId, tenantEmail, soon());
    }

    /** Rent ₹12,500 due on the 5th, deposit ₹25,000, and the tenant's mobile on the invite. */
    public ResultActions invite(Session landlord, String unitId, String tenantEmail, LocalDate startsOn)
            throws Exception {
        return authed(post("/api/v1/units/" + unitId + "/invites"), landlord, """
                {"tenantName":"Asha Rao","email":"%s","phone":"+919812345678","rentPaise":1250000,
                 "depositPaise":2500000,"dueDay":5,"startsOn":"%s"}
                """.formatted(tenantEmail, startsOn));
    }

    public String inviteToken(Session landlord, String unitId, String tenantEmail) throws Exception {
        return inviteToken(landlord, unitId, tenantEmail, soon());
    }

    /** Issues an invite and returns the token from its link, as the invitee would receive it. */
    public String inviteToken(Session landlord, String unitId, String tenantEmail, LocalDate startsOn)
            throws Exception {
        String body = invite(landlord, unitId, tenantEmail, startsOn).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String link = JsonPath.read(body, "$.link");
        return link.substring(link.lastIndexOf('/') + 1);
    }

    public ResultActions accept(String token, String password) throws Exception {
        return mvc.perform(post("/api/v1/invites/" + token + "/accept").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Asha Rao\",\"password\":\"" + password + "\"}"));
    }

    public Session acceptAsNewTenant(String token) throws Exception {
        return session(accept(token, PASSWORD).andExpect(status().isCreated()).andReturn());
    }

    public ResultActions get(Session session, String path) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(path).header(HttpHeaders.AUTHORIZATION, session.bearer()));
    }

    public String leaseIdFor(Session tenant) throws Exception {
        String body = get(tenant, "/api/v1/leases").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$[0].id");
    }

    public ResultActions authed(MockHttpServletRequestBuilder request, Session session, String json) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, session.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    public static Session session(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return new Session(JsonPath.read(body, "$.accessToken"), refreshCookie(result), JsonPath.read(body, "$.user.id"));
    }

    public static String refreshCookie(MvcResult result) {
        String header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        if (header == null || !header.startsWith("rb_refresh=")) {
            return null;
        }
        return header.substring("rb_refresh=".length(), header.indexOf(';'));
    }

    private static String id(ResultActions actions) throws Exception {
        return JsonPath.read(actions.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
