package com.rentbook;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.util.List;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

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

    /** Email goes nowhere; tests read what would have been sent. */
    @MockitoBean
    protected JavaMailSender mail;

    @Autowired
    protected MockMvc mvc;

    @LocalServerPort
    protected int port;

    protected Flows flows;

    @BeforeEach
    void startFromNow() {
        CLOCK.reset();
        flows = new Flows(mvc, this::emailedCode);
    }

    /** The newest signup code emailed to this address, taken from the message itself. */
    protected String emailedCode(String address) {
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, atLeastOnce()).send(sent.capture());
        return sent.getAllValues().reversed().stream()
                .filter(message -> List.of(message.getTo()).contains(address))
                .map(message -> message.getSubject().replaceAll("[^0-9]", ""))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No code was emailed to " + address));
    }
}
