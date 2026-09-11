package com.rentbook.invite;

import com.jayway.jsonpath.JsonPath;
import com.rentbook.Flows;
import com.rentbook.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Duration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InviteFlowIT extends IntegrationTest {

    @Test
    void aTenantJoinsABedByInviteAndBothPartiesReadTheSameLease() throws Exception {
        Flows.Session landlord = flows.registerLandlord(Flows.email("lata"));
        String pg = flows.createProperty(landlord, "Sunrise PG");
        String room = flows.addUnit(landlord, pg, "ROOM", "Room 201", null);
        String bedA = flows.addUnit(landlord, pg, "BED", "Bed A", room);
        flows.addUnit(landlord, pg, "BED", "Bed B", room);

        // A room that holds beds is let bed by bed.
        flows.invite(landlord, room, Flows.email("asha")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("room_has_beds"));

        String tenantEmail = Flows.email("asha");
        String token = flows.inviteToken(landlord, bedA, tenantEmail);
        flows.invite(landlord, bedA, Flows.email("someone")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("invite_pending"));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/invites/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitLabel").value("Bed A"))
                .andExpect(jsonPath("$.roomLabel").value("Room 201"))
                .andExpect(jsonPath("$.landlordName").value("Lata Iyer"))
                .andExpect(jsonPath("$.existingAccount").value(false));

        Flows.Session tenant = flows.acceptAsNewTenant(token);
        flows.get(tenant, "/api/v1/me").andExpect(jsonPath("$.role").value("TENANT"))
                .andExpect(jsonPath("$.email").value(tenantEmail));

        String leaseId = flows.leaseIdFor(tenant);
        flows.get(tenant, "/api/v1/leases/" + leaseId).andExpect(status().isOk())
                .andExpect(jsonPath("$.unit.label").value("Bed A"))
                .andExpect(jsonPath("$.landlord.fullName").value("Lata Iyer"));
        flows.get(landlord, "/api/v1/leases/" + leaseId).andExpect(status().isOk())
                .andExpect(jsonPath("$.tenant.email").value(tenantEmail));

        flows.get(landlord, "/api/v1/dashboard/landlord").andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.occupied").value(1))
                .andExpect(jsonPath("$.totals.vacant").value(1));
        flows.get(tenant, "/api/v1/dashboard/tenant").andExpect(status().isOk())
                .andExpect(jsonPath("$.lease.id").value(leaseId))
                .andExpect(jsonPath("$.nextRentPaise").value(1250000));

        // The link works once.
        flows.accept(token, Flows.PASSWORD).andExpect(status().isGone()).andExpect(jsonPath("$.code").value("invite_used"));
    }

    @Test
    void nobodyReadsALeaseTheyAreNotPartyTo() throws Exception {
        Flows.Session landlord = flows.registerLandlord(Flows.email("owner"));
        String pg = flows.createProperty(landlord, "Lakeview PG");
        String room = flows.addUnit(landlord, pg, "ROOM", "Room 1", null);
        String bedA = flows.addUnit(landlord, pg, "BED", "Bed A", room);
        String bedB = flows.addUnit(landlord, pg, "BED", "Bed B", room);
        Flows.Session first = flows.acceptAsNewTenant(flows.inviteToken(landlord, bedA, Flows.email("first")));
        Flows.Session second = flows.acceptAsNewTenant(flows.inviteToken(landlord, bedB, Flows.email("second")));
        String firstLease = flows.leaseIdFor(first);

        flows.get(second, "/api/v1/leases/" + firstLease).andExpect(status().isNotFound());

        Flows.Session stranger = flows.registerLandlord(Flows.email("stranger"));
        flows.get(stranger, "/api/v1/leases/" + firstLease).andExpect(status().isNotFound());
        flows.get(stranger, "/api/v1/properties/" + pg).andExpect(status().isNotFound());
        flows.invite(stranger, bedA, Flows.email("x")).andExpect(status().isNotFound());
        flows.authed(post("/api/v1/leases/" + firstLease + "/end"), stranger, "{\"endsOn\":\"2026-12-31\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    void revokedAndExpiredInvitesCannotBeUsed() throws Exception {
        Flows.Session landlord = flows.registerLandlord(Flows.email("revoker"));
        String pg = flows.createProperty(landlord, "Hillside PG");
        String flat = flows.addUnit(landlord, pg, "FLAT", "Flat 3B", null);

        String body = flows.invite(landlord, flat, Flows.email("revoked")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String inviteId = JsonPath.read(body, "$.invite.id");
        String link = JsonPath.read(body, "$.link");
        String revokedToken = link.substring(link.lastIndexOf('/') + 1);
        flows.authed(post("/api/v1/invites/" + inviteId + "/revoke"), landlord, "{}").andExpect(status().isOk());
        flows.accept(revokedToken, Flows.PASSWORD).andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("invite_revoked"));

        String expiringToken = flows.inviteToken(landlord, flat, Flows.email("late"));
        CLOCK.advance(Duration.ofDays(8));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/invites/" + expiringToken))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("invite_expired"));

        // An expired invite no longer holds the unit.
        flows.invite(landlord, flat, Flows.email("fresh")).andExpect(status().isCreated());
    }

    @Test
    void theInviteFixesTheEmailAndLandlordsCannotBeInvited() throws Exception {
        Flows.Session landlord = flows.registerLandlord(Flows.email("fixer"));
        String pg = flows.createProperty(landlord, "Palm PG");
        String flat = flows.addUnit(landlord, pg, "FLAT", "Flat 1A", null);

        String otherLandlordEmail = Flows.email("otherlandlord");
        flows.registerLandlord(otherLandlordEmail);
        flows.invite(landlord, flat, otherLandlordEmail).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("email_is_landlord"));

        String invited = Flows.email("invited");
        String token = flows.inviteToken(landlord, flat, invited);
        mvc.perform(post("/api/v1/invites/" + token + "/accept").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"attacker@example.in\",\"password\":\"" + Flows.PASSWORD + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value(invited))
                .andExpect(jsonPath("$.user.role").value("TENANT"));
    }
}
