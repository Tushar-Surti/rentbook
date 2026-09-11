package com.rentbook;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;

/**
 * The whole application against a real Postgres: Testcontainers by default, or an external database
 * when {@code rentbook.test.external-db=true} (see {@link TestcontainersConfiguration}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    protected static final MutableClock CLOCK = new MutableClock();

    @TestBean(name = "clock")
    Clock clock;

    static Clock clock() {
        return CLOCK;
    }

    @Autowired
    protected MockMvc mvc;

    @LocalServerPort
    protected int port;

    protected Flows flows;

    @BeforeEach
    void startFromNow() {
        CLOCK.reset();
        flows = new Flows(mvc);
    }
}
