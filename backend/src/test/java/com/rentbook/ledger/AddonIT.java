package com.rentbook.ledger;

import com.jayway.jsonpath.JsonPath;
import com.rentbook.Flows;
import com.rentbook.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Monthly add-ons: billed with the rent each month, made once per month, and stopped for good. */
class AddonIT extends IntegrationTest {

    @Autowired
    LedgerService ledger;

    @Autowired
    RentCycleJob rentCycle;

    @Test
    void anAddOnComesRoundWithTheRentUntilItIsStopped() throws Exception {
        Flows.Session landlord = flows.registerLandlord(Flows.email("lata"));
        String pg = flows.createProperty(landlord, "Addon PG");
        String flat = flows.addUnit(landlord, pg, "FLAT", "Flat 1", null);
        Flows.Session tenant = flows.acceptAsNewTenant(flows.inviteToken(landlord, flat, Flows.email("asha"), ledger.today()));
        String lease = flows.leaseIdFor(tenant);
        YearMonth thisMonth = YearMonth.from(ledger.today());

        String body = flows.authed(post("/api/v1/leases/" + lease + "/addons"), landlord, """
                        {"kind":"UTILITY","label":"Wi-Fi","amountPaise":50000,"startsMonth":"%s"}""".formatted(thisMonth))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.running").value(true))
                .andReturn().getResponse().getContentAsString();
        String addonId = JsonPath.read(body, "$.id");
        assertThat(wifiLines(tenant, lease)).hasSize(1);
        rentCycle.run();
        assertThat(wifiLines(tenant, lease)).hasSize(1);

        // A month on, it arrives with that month's rent.
        CLOCK.advance(Duration.ofDays(31));
        rentCycle.run();
        List<String> lines = wifiLines(tenant, lease);
        assertThat(lines).hasSize(2).anyMatch(description -> description.contains(thisMonth.plusMonths(1).getMonth()
                .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)));

        flows.get(tenant, "/api/v1/leases/" + lease + "/addons").andExpect(jsonPath("$[0].label").value("Wi-Fi"));
        flows.authed(post("/api/v1/addons/" + addonId + "/stop"), tenant, "{}").andExpect(status().isForbidden());
        flows.authed(post("/api/v1/addons/" + addonId + "/stop"), landlord, "{}")
                .andExpect(jsonPath("$.running").value(false));
        CLOCK.advance(Duration.ofDays(31));
        rentCycle.run();
        assertThat(wifiLines(tenant, lease)).hasSize(2);
    }

    @Test
    void itStartsThisMonthOrLaterAndOnlyTheLandlordSetsItUp() throws Exception {
        Flows.Session landlord = flows.registerLandlord(Flows.email("lata"));
        String pg = flows.createProperty(landlord, "Rules PG");
        String flat = flows.addUnit(landlord, pg, "FLAT", "Flat 1", null);
        Flows.Session tenant = flows.acceptAsNewTenant(flows.inviteToken(landlord, flat, Flows.email("asha"), ledger.today()));
        String lease = flows.leaseIdFor(tenant);
        String past = YearMonth.from(ledger.today()).minusMonths(1).toString();
        flows.authed(post("/api/v1/leases/" + lease + "/addons"), landlord, """
                {"kind":"OTHER","label":"Meals","amountPaise":300000,"startsMonth":"%s"}""".formatted(past))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("starts_in_past"));
        flows.authed(post("/api/v1/leases/" + lease + "/addons"), tenant, """
                {"kind":"OTHER","label":"Meals","amountPaise":300000,"startsMonth":"%s"}""".formatted(YearMonth.from(ledger.today())))
                .andExpect(status().isForbidden());
        Flows.Session stranger = flows.registerLandlord(Flows.email("other"));
        flows.get(stranger, "/api/v1/leases/" + lease + "/addons").andExpect(status().isNotFound());
    }

    private List<String> wifiLines(Flows.Session who, String lease) throws Exception {
        String body = flows.get(who, "/api/v1/leases/" + lease + "/ledger").andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.entries[?(@.description =~ /Wi-Fi.*/)].description");
    }
}
