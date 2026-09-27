package com.rentbook.realtime;

import com.rentbook.caretaker.CaretakerWork;
import com.rentbook.lease.LeaseService;
import com.rentbook.maintenance.TicketService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authenticates STOMP sessions with the same JWT decoder as the REST API and decides who may
 * subscribe to what. Clients only listen; SEND frames are refused.
 */
@Component
class StompAuthInterceptor implements ChannelInterceptor {

    private static final Pattern LEASE_TOPIC = Pattern.compile("^/topic/leases/([0-9a-fA-F-]{36})$");
    private static final Pattern TICKET_TOPIC = Pattern.compile("^/topic/tickets/([0-9a-fA-F-]{36})$");
    private static final String PERSONAL_QUEUE = "/user/queue/events";

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter converter;
    private final LeaseService leases;
    private final TicketService tickets;
    // Looked up when a caretaker subscribes, not at startup: the broker is configured before the services exist.
    private final ObjectProvider<CaretakerWork> caretakers;

    StompAuthInterceptor(JwtDecoder jwtDecoder, JwtAuthenticationConverter converter, LeaseService leases,
                         TicketService tickets, ObjectProvider<CaretakerWork> caretakers) {
        this.jwtDecoder = jwtDecoder;
        this.converter = converter;
        this.leases = leases;
        this.tickets = tickets;
        this.caretakers = caretakers;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT -> accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
            case SUBSCRIBE -> authorize(accessor);
            case SEND -> throw new MessagingException("This connection is receive-only");
            default -> { }
        }
        return message;
    }

    private JwtAuthenticationToken authenticate(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessagingException("Missing access token");
        }
        try {
            Jwt jwt = jwtDecoder.decode(header.substring("Bearer ".length()));
            return (JwtAuthenticationToken) converter.convert(jwt);
        } catch (JwtException e) {
            throw new MessagingException("Invalid or expired access token");
        }
    }

    private void authorize(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof JwtAuthenticationToken user)) {
            throw new MessagingException("Not authenticated");
        }
        String destination = accessor.getDestination();
        if (PERSONAL_QUEUE.equals(destination)) {
            return;
        }
        if (destination != null) {
            UUID userId = UUID.fromString(user.getName());
            Matcher lease = LEASE_TOPIC.matcher(destination);
            boolean caretaker = user.getAuthorities().stream()
                    .anyMatch(authority -> "ROLE_CARETAKER".equals(authority.getAuthority()));
            if (lease.matches() && (caretaker
                    ? caretakers.getObject().canWatchLease(userId, UUID.fromString(lease.group(1)))
                    : leases.isParty(UUID.fromString(lease.group(1)), userId))) {
                return;
            }
            Matcher ticket = TICKET_TOPIC.matcher(destination);
            if (ticket.matches() && (caretaker
                    ? caretakers.getObject().canWatchTicket(userId, UUID.fromString(ticket.group(1)))
                    : tickets.isParty(UUID.fromString(ticket.group(1)), userId))) {
                return;
            }
        }
        throw new MessagingException("Not allowed to subscribe to " + destination);
    }
}
