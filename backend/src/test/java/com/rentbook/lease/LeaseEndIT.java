package com.rentbook.lease;

import com.rentbook.Flows;
import com.rentbook.IntegrationTest;
import com.rentbook.ledger.LedgerService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The landlord sets a tenant's last day: on notice until then, and the bed is free again after it. */
class LeaseEndIT extends IntegrationTest {

    @Autowired
    LeaseService leases;

    @Autowired
    LedgerService ledger;

    @Autowired
    JdbcTemplate jdbc;

    private record Tenancy(Flows.Session landlord, Flows.Session tenant, String leaseId, String unitId) {
    }

    @Test
    void aFutureLastDayPutsTheLeaseOnNoticeAndItEndsOnTheDay() throws Exception {
        Tenancy home = moveIn();
        LocalDate lastDay = today().plusDays(30);

        end(home.landlord(), home, lastDay)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOTICE"))
                .andExpect(jsonPath("$.endsOn").value(lastDay.toString()));
        flows.get(home.tenant(), "/api/v1/leases/" + home.leaseId()).andExpect(jsonPath("$.status").value("NOTICE"));
        assertThat(unitStatus(home)).isEqualTo("OCCUPIED");

        // The date can still move while the lease is on notice.
        end(home.landlord(), home, lastDay.plusDays(5)).andExpect(jsonPath("$.status").value("NOTICE"));

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, timeout(5_000).atLeast(2)).send(sent.capture());
        assertThat(sent.getAllValues()).anySatisfy(message -> {
            assertThat(message.getSubject()).startsWith("Your move-out date: ");
            assertThat(message.getText()).contains("Lata Iyer has set", "as your last day at Flat 2B, Notice PG");
        });

        leases.endLeasesDue(lastDay.plusDays(5));
        flows.get(home.landlord(), "/api/v1/leases/" + home.leaseId()).andExpect(jsonPath("$.status").value("ENDED"));
        assertThat(unitStatus(home)).isEqualTo("VACANT");
    }

    @Test
    void endingItTodayFreesTheBedAtOnceForTheNextTenant() throws Exception {
        Tenancy home = moveIn();
        // Next month's rent, as if it had already been added ahead of its due date.
        LocalDate nextMonth = today().plusMonths(1).withDayOfMonth(1);
        jdbc.update("""
                insert into charges (id, lease_id, kind, period_month, description, amount_paise, due_on, status,
                    created_at, updated_at)
                values (?, ?, 'RENT', ?, 'Rent for next month', 1250000, ?, 'DUE', now(), now())""",
                UUID.randomUUID(), UUID.fromString(home.leaseId()), nextMonth, nextMonth);

        end(home.landlord(), home, today()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ENDED"));
        assertThat(unitStatus(home)).isEqualTo("VACANT");

        // Rent for a month after the last day is never owed; this month's stays.
        flows.get(home.landlord(), "/api/v1/leases/" + home.leaseId() + "/ledger")
                .andExpect(jsonPath("$.entries[?(@.description == 'Rent for next month')].status").value("WAIVED"))
                .andExpect(jsonPath("$.outstandingPaise").value(3_750_000));

        // The tenant's home shows no active lease, and the landlord can invite someone new.
        flows.get(home.tenant(), "/api/v1/dashboard/tenant").andExpect(jsonPath("$.lease").doesNotExist());
        flows.invite(home.landlord(), home.unitId(), Flows.email("next")).andExpect(status().isCreated());

        end(home.landlord(), home, today()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("lease_ended"));
    }

    @Test
    void onlyItsLandlordEndsItAndNeverBeforeItStarted() throws Exception {
        Tenancy home = moveIn();
        end(home.tenant(), home, today()).andExpect(status().isForbidden());
        end(home.landlord(), home, today().minusDays(1)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("end_before_start"));
        flows.get(home.landlord(), "/api/v1/leases/" + home.leaseId()).andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    private Tenancy moveIn() throws Exception {
        Flows.Session landlord = flows.registerLandlord(Flows.email("lata"));
        String pg = flows.createProperty(landlord, "Notice PG");
        String flat = flows.addUnit(landlord, pg, "FLAT", "Flat 2B", null);
        Flows.Session tenant = flows.acceptAsNewTenant(flows.inviteToken(landlord, flat, Flows.email("asha"), today()));
        return new Tenancy(landlord, tenant, flows.leaseIdFor(tenant), flat);
    }

    private ResultActions end(Flows.Session who, Tenancy home, LocalDate lastDay) throws Exception {
        return flows.authed(post("/api/v1/leases/" + home.leaseId() + "/end"), who, "{\"endsOn\":\"" + lastDay + "\"}");
    }

    private String unitStatus(Tenancy home) {
        return jdbc.queryForObject("select status from units where id = ?", String.class, UUID.fromString(home.unitId()));
    }

    private LocalDate today() {
        return ledger.today();
    }
}
