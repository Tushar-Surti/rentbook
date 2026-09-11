package com.rentbook.ledger;

import com.jayway.jsonpath.JsonPath;
import com.rentbook.Flows;
import com.rentbook.IntegrationTest;
import com.rentbook.common.IndiaTime;
import com.rentbook.lease.Lease;
import com.rentbook.lease.LeaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LedgerFlowIT extends IntegrationTest {

    @Autowired
    LedgerService ledger;

    @Autowired
    LeaseRepository leases;

    @Autowired
    ChargeRepository charges;

    @Autowired
    ReminderJob reminders;

    @Autowired
    JdbcTemplate jdbc;

    private record Tenancy(Flows.Session landlord, Flows.Session tenant, String leaseId) {
        String ledgerPath() {
            return "/api/v1/leases/" + leaseId + "/ledger";
        }
    }

    @Test
    void aNewMoveInOpensWithItsDepositAndFirstRentAndBothPartiesReadTheSameLedger() throws Exception {
        Tenancy home = moveIn(today());

        String tenantView = flows.get(home.tenant(), home.ledgerPath()).andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[?(@.kind == 'DEPOSIT')].amountPaise", hasItem(2500000)))
                .andExpect(jsonPath("$.entries[?(@.kind == 'RENT')].amountPaise", hasItem(1250000)))
                .andExpect(jsonPath("$.outstandingPaise").value(3750000))
                .andReturn().getResponse().getContentAsString();
        String landlordView = flows.get(home.landlord(), home.ledgerPath()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(landlordView).isEqualTo(tenantView);
        flows.get(home.tenant(), "/api/v1/dashboard/tenant").andExpect(status().isOk())
                .andExpect(jsonPath("$.outstandingPaise").value(3750000))
                .andExpect(jsonPath("$.outstanding.length()").value(2));
    }

    @Test
    void rentIsGeneratedOnceAMonthAndNeverBackdated() throws Exception {
        // A tenant who moved in months before the landlord started using Rentbook.
        Tenancy home = moveIn(today().minusMonths(4));
        Lease lease = leases.findById(UUID.fromString(home.leaseId())).orElseThrow();
        LocalDate recordedOn = LocalDate.ofInstant(lease.getCreatedAt(), IndiaTime.ZONE);
        LocalDate later = today().plusMonths(3);

        ledger.syncRent(lease, later);
        assertThat(ledger.syncRent(lease, later)).isZero();

        List<Charge> all = charges.findByLeaseIdOrderByDueOnAscCreatedAtAsc(lease.getId());
        assertThat(all).noneMatch(charge -> charge.getKind() == Charge.Kind.DEPOSIT);
        List<Charge> rent = all.stream().filter(charge -> charge.getKind() == Charge.Kind.RENT).toList();
        assertThat(rent).hasSizeGreaterThanOrEqualTo(3)
                .allMatch(charge -> !charge.getDueOn().isBefore(recordedOn))
                .allMatch(charge -> !charge.getDueOn().isAfter(later.plusDays(LedgerService.LEAD_DAYS)));
        List<YearMonth> periods = rent.stream().map(charge -> YearMonth.from(charge.getPeriodMonth())).toList();
        for (int i = 1; i < periods.size(); i++) {
            assertThat(periods.get(i)).isEqualTo(periods.get(i - 1).plusMonths(1));
        }
    }

    @Test
    void theLandlordAddsAndWaivesChargesOnTheSharedLedger() throws Exception {
        Tenancy home = moveIn(today());
        String chargeId = addElectricity(home, today().plusDays(5));
        flows.get(home.tenant(), home.ledgerPath()).andExpect(jsonPath("$.outstandingPaise").value(3750000 + 180000));

        flows.authed(post("/api/v1/charges/" + chargeId + "/waive"), home.landlord(), "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAIVED"));
        flows.get(home.tenant(), home.ledgerPath()).andExpect(jsonPath("$.outstandingPaise").value(3750000));
        flows.authed(post("/api/v1/charges/" + chargeId + "/waive"), home.landlord(), "{}")
                .andExpect(status().isConflict());

        flows.authed(post("/api/v1/leases/" + home.leaseId() + "/charges"), home.landlord(), """
                        {"kind":"RENT","description":"Extra rent","amountPaise":100,"dueOn":"%s"}""".formatted(today()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("charge_kind"));
    }

    @Test
    void onlyThePartiesToALeaseReadOrChangeItsLedger() throws Exception {
        Tenancy home = moveIn(today());
        Tenancy other = moveIn(today());
        String chargeId = JsonPath.read(flows.get(home.tenant(), home.ledgerPath())
                .andReturn().getResponse().getContentAsString(), "$.entries[0].id");

        flows.get(other.tenant(), home.ledgerPath()).andExpect(status().isNotFound());
        flows.get(other.landlord(), home.ledgerPath()).andExpect(status().isNotFound());
        flows.authed(post("/api/v1/charges/" + chargeId + "/waive"), other.landlord(), "{}")
                .andExpect(status().isNotFound());
        flows.authed(post("/api/v1/leases/" + home.leaseId() + "/charges"), other.landlord(), """
                        {"kind":"OTHER","description":"Cleaning","amountPaise":50000,"dueOn":"%s"}""".formatted(today()))
                .andExpect(status().isNotFound());
    }

    @Test
    void eachReminderGoesOutOnce() throws Exception {
        Tenancy home = moveIn(today());
        String chargeId = addElectricity(home, today().plusDays(3));
        String keys = "reminder:" + chargeId + ":rent.before:%";

        assertThat(reminders.run(today())).isPositive();
        assertThat(count(keys)).isEqualTo(2);
        // The tenant's mobile came from the invite; texts go to the log adapter in tests.
        assertThat(jdbc.queryForObject("select status from notifications where dedupe_key = ?", String.class,
                "reminder:" + chargeId + ":rent.before:sms")).isEqualTo("SENT");

        reminders.run(today());
        assertThat(count(keys)).isEqualTo(2);
    }

    private Tenancy moveIn(LocalDate startsOn) throws Exception {
        Flows.Session landlord = flows.registerLandlord(Flows.email("lata"));
        String pg = flows.createProperty(landlord, "Ledger PG");
        String flat = flows.addUnit(landlord, pg, "FLAT", "Flat 4A", null);
        Flows.Session tenant = flows.acceptAsNewTenant(flows.inviteToken(landlord, flat, Flows.email("asha"), startsOn));
        return new Tenancy(landlord, tenant, flows.leaseIdFor(tenant));
    }

    private String addElectricity(Tenancy home, LocalDate dueOn) throws Exception {
        String body = flows.authed(post("/api/v1/leases/" + home.leaseId() + "/charges"), home.landlord(), """
                        {"kind":"UTILITY","description":"Electricity for September","amountPaise":180000,"dueOn":"%s"}
                        """.formatted(dueOn))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private int count(String dedupeKeyPattern) {
        Integer rows = jdbc.queryForObject("select count(*) from notifications where dedupe_key like ?",
                Integer.class, dedupeKeyPattern);
        return rows == null ? 0 : rows;
    }

    private LocalDate today() {
        return ledger.today();
    }
}
