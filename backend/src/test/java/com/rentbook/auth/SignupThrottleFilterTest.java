package com.rentbook.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SignupThrottleFilterTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-11T10:00:00Z"));
    private final SignupThrottleFilter filter = new SignupThrottleFilter(3, new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    });

    @Test
    void eachAddressGetsSoManySignupsAnHour() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(post("/api/v1/auth/register/code", "10.0.0.1").getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse refused = post("/api/v1/invites/some-token/accept", "10.0.0.1");
        assertThat(refused.getStatus()).isEqualTo(429);
        assertThat(refused.getHeader("Retry-After")).isEqualTo("3600");
        assertThat(refused.getContentAsString()).contains("too_many_signups");

        assertThat(post("/api/v1/auth/register/code", "10.0.0.2").getStatus()).isEqualTo(200);
        now.set(now.get().plus(Duration.ofHours(1)));
        assertThat(post("/api/v1/auth/register/code", "10.0.0.1").getStatus()).isEqualTo(200);
    }

    @Test
    void otherRequestsAreNotCounted() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(post("/api/v1/auth/login", "10.0.0.3").getStatus()).isEqualTo(200);
        }
        assertThat(post("/api/v1/auth/register/code", "10.0.0.3").getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse post(String path, String address) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(address);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
