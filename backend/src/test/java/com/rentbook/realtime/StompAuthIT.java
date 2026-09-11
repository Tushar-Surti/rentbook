package com.rentbook.realtime;

import com.jayway.jsonpath.JsonPath;
import com.rentbook.Flows;
import com.rentbook.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.AbstractMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class StompAuthIT extends IntegrationTest {

    private final WebSocketStompClient client = stompClient();

    @AfterEach
    void stopClient() {
        client.stop();
    }

    @Test
    void connectingWithoutATokenIsRefused() throws Exception {
        Listener listener = new Listener();
        client.connectAsync(url(), new WebSocketHttpHeaders(), new StompHeaders(), listener);
        assertThat(listener.errors.get(5, TimeUnit.SECONDS)).contains("Missing access token");
        assertThat(listener.connected).isNotDone();
    }

    @Test
    void aTenantCannotListenToSomeoneElsesLease() throws Exception {
        Flows.Session landlord = flows.registerLandlord(Flows.email("lata"));
        String pg = flows.createProperty(landlord, "Sunrise PG");
        String room = flows.addUnit(landlord, pg, "ROOM", "Room 3", null);
        String bedA = flows.addUnit(landlord, pg, "BED", "Bed A", room);
        String bedB = flows.addUnit(landlord, pg, "BED", "Bed B", room);
        Flows.Session first = flows.acceptAsNewTenant(flows.inviteToken(landlord, bedA, Flows.email("first")));
        Flows.Session second = flows.acceptAsNewTenant(flows.inviteToken(landlord, bedB, Flows.email("second")));

        Listener listener = new Listener();
        StompSession session = connect(second, listener);
        session.subscribe("/topic/leases/" + flows.leaseIdFor(first), listener);
        assertThat(listener.errors.get(5, TimeUnit.SECONDS)).contains("Not allowed to subscribe");
    }

    @Test
    void theLandlordHearsTheMoveInLive() throws Exception {
        Flows.Session landlord = flows.registerLandlord(Flows.email("lata"));
        String pg = flows.createProperty(landlord, "Garden PG");
        String flat = flows.addUnit(landlord, pg, "FLAT", "Flat 2C", null);
        String token = flows.inviteToken(landlord, flat, Flows.email("asha"));

        Listener listener = new Listener();
        StompSession session = connect(landlord, listener);
        session.subscribe("/user/queue/events", listener);
        Thread.sleep(300); // let the SUBSCRIBE frame land before the event is published

        flows.acceptAsNewTenant(token);
        String event = listener.messages.get(5, TimeUnit.SECONDS);
        assertThat(event).contains("\"type\":\"invite.accepted\"").contains("Asha Rao");
    }

    @Test
    void onlyARequestsTwoPartiesHearItsThread() throws Exception {
        Flows.Session landlord = flows.registerLandlord(Flows.email("lata"));
        String pg = flows.createProperty(landlord, "Hill PG");
        String flatOne = flows.addUnit(landlord, pg, "FLAT", "Flat 1", null);
        String flatTwo = flows.addUnit(landlord, pg, "FLAT", "Flat 2", null);
        Flows.Session asha = flows.acceptAsNewTenant(flows.inviteToken(landlord, flatOne, Flows.email("asha")));
        Flows.Session ravi = flows.acceptAsNewTenant(flows.inviteToken(landlord, flatTwo, Flows.email("ravi")));
        String opened = flows.authed(post("/api/v1/tickets"), asha, """
                        {"leaseId":"%s","title":"Geyser not heating","category":"APPLIANCE","priority":"NORMAL",
                         "body":"No hot water since morning."}""".formatted(flows.leaseIdFor(asha)))
                .andReturn().getResponse().getContentAsString();
        String ticketId = JsonPath.read(opened, "$.ticket.id");

        Listener stranger = new Listener();
        connect(ravi, stranger).subscribe("/topic/tickets/" + ticketId, stranger);
        assertThat(stranger.errors.get(5, TimeUnit.SECONDS)).contains("Not allowed to subscribe");

        Listener owner = new Listener();
        connect(landlord, owner).subscribe("/topic/tickets/" + ticketId, owner);
        Thread.sleep(300); // let the SUBSCRIBE frame land before the event is published
        flows.authed(post("/api/v1/tickets/" + ticketId + "/events"), asha, "{\"body\":\"Still cold.\"}");
        assertThat(owner.messages.get(5, TimeUnit.SECONDS))
                .contains("\"type\":\"ticket.message\"").contains("Geyser not heating");
    }

    private StompSession connect(Flows.Session who, Listener listener) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", who.bearer());
        return client.connectAsync(url(), new WebSocketHttpHeaders(), headers, listener).get(5, TimeUnit.SECONDS);
    }

    private String url() {
        return "ws://localhost:" + port + "/ws";
    }

    private static WebSocketStompClient stompClient() {
        WebSocketStompClient stomp = new WebSocketStompClient(new StandardWebSocketClient());
        stomp.setDefaultHeartbeat(new long[] {0, 0});
        // Payloads are read as raw JSON text; the assertions look at the text.
        stomp.setMessageConverter(new AbstractMessageConverter(List.of()) {
            @Override
            protected boolean supports(Class<?> clazz) {
                return String.class == clazz;
            }

            @Override
            protected Object convertFromInternal(Message<?> message, Class<?> targetClass, Object hint) {
                return new String((byte[]) message.getPayload(), StandardCharsets.UTF_8);
            }
        });
        return stomp;
    }

    /** Collects the first message, the first error, and whether the session connected. */
    private static final class Listener extends StompSessionHandlerAdapter implements StompFrameHandler {

        final CompletableFuture<String> messages = new CompletableFuture<>();
        final CompletableFuture<String> errors = new CompletableFuture<>();
        final CompletableFuture<StompSession> connected = new CompletableFuture<>();

        @Override
        public void afterConnected(StompSession session, StompHeaders headers) {
            connected.complete(session);
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return String.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            if (headers.getFirst("message") != null && !headers.containsKey("subscription")) {
                errors.complete(headers.getFirst("message"));
            } else {
                messages.complete((String) payload);
            }
        }

        @Override
        public void handleException(StompSession session, StompCommand command, StompHeaders headers,
                                    byte[] payload, Throwable exception) {
            errors.complete(String.valueOf(exception.getMessage()));
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            errors.complete(String.valueOf(exception.getMessage()));
        }
    }
}
