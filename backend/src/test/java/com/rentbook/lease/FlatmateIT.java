package com.rentbook.lease;

import com.jayway.jsonpath.JsonPath;
import com.rentbook.Flows;
import com.rentbook.IntegrationTest;
import com.rentbook.ledger.LedgerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Friends sharing one flat: each on a lease of their own share, all on one line of the landlord's register. */
class FlatmateIT extends IntegrationTest {

    @Autowired
    LedgerService ledger;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flatmatesShareAFlatEachWithTheirOwnShareAndBook() throws Exception {
        Flows.Session landlord = flows.registerLandlord(Flows.email("lata"));
        String pg = flows.createProperty(landlord, "Shared Homes");
        String flat = flows.addUnit(landlord, pg, "FLAT", "Flat 4A", null);
        String room = flows.addUnit(landlord, pg, "ROOM", "Room 9", null);
        String bed = flows.addUnit(landlord, pg, "BED", "Bed A", room);
        Flows.Session asha = flows.acceptAsNewTenant(flows.inviteToken(landlord, flat, Flows.email("asha"), today()));
        String ashaLease = flows.leaseIdFor(asha);

        // A second ordinary tenant can't move in; a flatmate can, for their own share.
        flows.invite(landlord, flat, Flows.email("x"), today()).andExpect(status().isConflict());
        invite(landlord, bed, Flows.email("y"), 800_000).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("not_shareable"));
        String link = JsonPath.read(invite(landlord, flat, Flows.email("priya"), 1_000_000)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.link");
        String token = link.substring(link.lastIndexOf('/') + 1);
        mvc.perform(get("/api/v1/invites/" + token))
                .andExpect(jsonPath("$.flatmate").value(true))
                .andExpect(jsonPath("$.sharedWith[0]").value("Asha Rao"));
        Flows.Session priya = Flows.session(mvc.perform(post("/api/v1/invites/" + token + "/accept")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Priya Sharma\",\"password\":\"" + Flows.PASSWORD + "\"}"))
                .andExpect(status().isCreated()).andReturn());
        String priyaLease = flows.leaseIdFor(priya);
        assertThat(priyaLease).isNotEqualTo(ashaLease);

        // One line of the register, two people, both shares counted.
        String board = flows.get(landlord, "/api/v1/dashboard/landlord").andReturn().getResponse().getContentAsString();
        java.util.List<String> sharing = JsonPath.read(board, "$.properties[0].hooks[?(@.label == 'Flat 4A')].flatmates[*].tenantName");
        assertThat(sharing).containsExactly("Priya Sharma");
        flows.get(landlord, "/api/v1/dashboard/landlord")
                .andExpect(jsonPath("$.totals.monthlyRentPaise").value(1_250_000 + 1_000_000));
        assertThat(board).contains("\"tenantName\":\"Asha Rao\"");

        // Each reads their own book and sees the other's share and month.
        flows.get(priya, "/api/v1/leases/" + priyaLease + "/ledger")
                .andExpect(jsonPath("$.entries[?(@.kind == 'RENT')].amountPaise").value(org.hamcrest.Matchers.hasItem(1_000_000)));
        flows.get(priya, "/api/v1/leases/" + ashaLease + "/ledger").andExpect(status().isNotFound());
        flows.get(asha, "/api/v1/dashboard/tenant")
                .andExpect(jsonPath("$.flatmates.length()").value(1))
                .andExpect(jsonPath("$.flatmates[0].rentPaise").value(1_000_000));
        flows.get(priya, "/api/v1/dashboard/tenant")
                .andExpect(jsonPath("$.flatmates[0].name").value("Asha Rao"));

        // One moves out: still lived in. The last moves out: vacant.
        end(landlord, ashaLease).andExpect(status().isOk());
        assertThat(unitStatus(flat)).isEqualTo("OCCUPIED");
        flows.get(priya, "/api/v1/dashboard/tenant").andExpect(jsonPath("$.flatmates.length()").value(0));
        end(landlord, priyaLease).andExpect(status().isOk());
        assertThat(unitStatus(flat)).isEqualTo("VACANT");
    }

    private ResultActions invite(Flows.Session landlord, String unitId, String email, long rentPaise) throws Exception {
        return flows.authed(post("/api/v1/units/" + unitId + "/invites"), landlord, """
                {"tenantName":"Priya Sharma","email":"%s","rentPaise":%d,"depositPaise":2000000,"dueDay":%d,
                 "startsOn":"%s","flatmate":true}""".formatted(email, rentPaise, Math.min(today().getDayOfMonth(), 28), today()));
    }

    private ResultActions end(Flows.Session landlord, String leaseId) throws Exception {
        return flows.authed(post("/api/v1/leases/" + leaseId + "/end"), landlord, "{\"endsOn\":\"" + today() + "\"}");
    }

    private String unitStatus(String unitId) {
        return jdbc.queryForObject("select status from units where id = ?", String.class, UUID.fromString(unitId));
    }

    private LocalDate today() {
        return ledger.today();
    }
}
