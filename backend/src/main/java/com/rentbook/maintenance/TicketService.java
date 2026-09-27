package com.rentbook.maintenance;

import com.rentbook.common.ApiException;
import com.rentbook.document.DocumentService;
import com.rentbook.lease.Lease;
import com.rentbook.lease.LeaseService;
import com.rentbook.user.Role;
import com.rentbook.user.User;
import com.rentbook.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.rentbook.maintenance.Ticket.Status.ACKNOWLEDGED;
import static com.rentbook.maintenance.Ticket.Status.CLOSED;
import static com.rentbook.maintenance.Ticket.Status.IN_PROGRESS;
import static com.rentbook.maintenance.Ticket.Status.OPEN;
import static com.rentbook.maintenance.Ticket.Status.RESOLVED;

/**
 * Maintenance requests: one thread per request that both parties write on and neither can edit. The
 * landlord moves a request forward; the tenant closes it, or reopens it when the fix didn't hold.
 */
@Service
public class TicketService {

    private static final Map<Ticket.Status, Set<Ticket.Status>> LANDLORD_MOVES = Map.of(
            OPEN, EnumSet.of(ACKNOWLEDGED, IN_PROGRESS, RESOLVED),
            ACKNOWLEDGED, EnumSet.of(IN_PROGRESS, RESOLVED),
            IN_PROGRESS, EnumSet.of(RESOLVED));

    private static final Map<Ticket.Status, Set<Ticket.Status>> TENANT_MOVES = Map.of(
            OPEN, EnumSet.of(CLOSED),
            ACKNOWLEDGED, EnumSet.of(CLOSED),
            IN_PROGRESS, EnumSet.of(CLOSED),
            RESOLVED, EnumSet.of(OPEN, CLOSED),
            CLOSED, EnumSet.of(OPEN));

    private final TicketRepository tickets;
    private final TicketEventRepository events;
    private final LeaseService leases;
    private final UserRepository users;
    private final DocumentService documents;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    TicketService(TicketRepository tickets, TicketEventRepository events, LeaseService leases, UserRepository users,
                  DocumentService documents, ApplicationEventPublisher publisher, Clock clock) {
        this.tickets = tickets;
        this.events = events;
        this.leases = leases;
        this.users = users;
        this.documents = documents;
        this.publisher = publisher;
        this.clock = clock;
    }

    public record TicketView(UUID id, UUID leaseId, String title, Ticket.Category category, Ticket.Priority priority,
                             Ticket.Status status, Instant openedAt, Instant lastActivityAt,
                             LeaseService.UnitRef unit, LeaseService.PropertyRef property,
                             LeaseService.PersonRef tenant, LeaseService.PersonRef landlord) {
    }

    public record Author(UUID id, String name, Role role) {
    }

    public record EventView(UUID id, TicketEvent.Kind kind, String body, Ticket.Status fromStatus,
                            Ticket.Status toStatus, Instant at, Author author, List<DocumentService.PhotoRef> photos) {
    }

    /** The request, its thread in order, and the statuses this reader may move it to next. */
    public record Thread(TicketView ticket, List<EventView> events, List<Ticket.Status> nextStatuses) {
    }

    public record NewTicket(UUID leaseId, String title, Ticket.Category category, Ticket.Priority priority,
                            String body, List<UUID> photoIds) {
    }

    /** Published inside the transaction; {@link TicketNotifications} tells both parties after commit. */
    public record TicketChanged(UUID ticketId, UUID leaseId, UUID landlordId, UUID tenantId, UUID actorId,
                                String actorName, String title, String kind, Ticket.Status status,
                                Ticket.Priority priority) {
    }

    @Transactional
    public Thread open(UUID tenantId, NewTicket request) {
        Lease lease = leases.require(request.leaseId(), tenantId, Role.TENANT);
        if (!lease.isLive()) {
            throw ApiException.conflict("lease_ended", "This lease has ended, so it can't take new requests.");
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Ticket ticket = tickets.save(new Ticket(lease, request.title().strip(), request.category(), request.priority(),
                now));
        TicketEvent first = events.save(TicketEvent.message(ticket.getId(), tenantId, request.body().strip(), now));
        documents.attachPhotos(request.photoIds(), first.getId(), tenantId, lease.getId());
        publish(ticket, tenantId, "opened");
        return thread(ticket, tenantId, Role.TENANT);
    }

    /** A landlord's requests (optionally one property's, optionally only those still open), or a tenant's own. */
    @Transactional(readOnly = true)
    public List<TicketView> list(UUID userId, Role role, UUID propertyId, boolean openOnly) {
        List<Ticket> found = role == Role.LANDLORD
                ? tickets.findByLandlordIdOrderByLastActivityAtDesc(userId)
                : tickets.findByTenantIdOrderByLastActivityAtDesc(userId);
        Map<UUID, LeaseService.LeaseView> byLease = leases.visibleTo(userId, role).stream()
                .collect(Collectors.toMap(LeaseService.LeaseView::id, Function.identity()));
        return found.stream()
                .filter(ticket -> !openOnly || ticket.isOpen())
                .filter(ticket -> byLease.containsKey(ticket.getLeaseId()))
                .map(ticket -> view(ticket, byLease.get(ticket.getLeaseId())))
                .filter(view -> propertyId == null || view.property().id().equals(propertyId))
                .toList();
    }

    @Transactional(readOnly = true)
    public Thread thread(UUID ticketId, UUID userId, Role role) {
        return thread(require(ticketId, userId, role), userId, role);
    }

    @Transactional
    public EventView post(UUID ticketId, UUID userId, Role role, String body, List<UUID> photoIds) {
        Ticket ticket = require(ticketId, userId, role);
        if (ticket.getStatus() == CLOSED) {
            throw ApiException.conflict("ticket_closed", "This request is closed. Reopen it to add to it.");
        }
        boolean noWords = body == null || body.isBlank();
        if (noWords && (photoIds == null || photoIds.isEmpty())) {
            throw ApiException.badRequest("empty_message", "Write something or attach a photo.");
        }
        Instant at = next(ticket);
        TicketEvent event = events.save(TicketEvent.message(ticket.getId(), userId, noWords ? "" : body.strip(), at));
        ticket.touch(at);
        documents.attachPhotos(photoIds, event.getId(), userId, ticket.getLeaseId());
        publish(ticket, userId, "message");
        return eventViews(List.of(event)).getFirst();
    }

    @Transactional
    public Thread move(UUID ticketId, UUID userId, Role role, Ticket.Status to) {
        Ticket ticket = require(ticketId, userId, role);
        Ticket.Status from = ticket.getStatus();
        if (!movesFor(role, from).contains(to)) {
            throw ApiException.conflict("status_change", role == Role.TENANT
                    ? "You can close this request, or reopen it once it's resolved."
                    : "A request moves forward: acknowledged, in progress, then resolved. The tenant closes or reopens it.");
        }
        Instant at = next(ticket);
        ticket.move(to, at);
        events.save(TicketEvent.statusChange(ticket.getId(), userId, from, to, at));
        publish(ticket, userId, "status");
        return thread(ticket, userId, role);
    }

    /** A request in the landlord's book, read by someone working for them; the caller checks the property. */
    @Transactional(readOnly = true)
    public Thread threadOnBehalf(UUID ticketId, UUID landlordId) {
        return thread(require(ticketId, landlordId, Role.LANDLORD), landlordId, Role.LANDLORD);
    }

    /** A message written by the landlord's caretaker ({@code authorId}), with the landlord's authority. */
    @Transactional
    public EventView postOnBehalf(UUID ticketId, UUID landlordId, UUID authorId, String body) {
        Ticket ticket = require(ticketId, landlordId, Role.LANDLORD);
        if (ticket.getStatus() == CLOSED) {
            throw ApiException.conflict("ticket_closed", "This request is closed. The tenant can reopen it.");
        }
        if (body == null || body.isBlank()) {
            throw ApiException.badRequest("empty_message", "Write something first.");
        }
        Instant at = next(ticket);
        TicketEvent event = events.save(TicketEvent.message(ticket.getId(), authorId, body.strip(), at));
        ticket.touch(at);
        publish(ticket, authorId, "message");
        return eventViews(List.of(event)).getFirst();
    }

    /** A status move made by the landlord's caretaker, with the landlord's moves. */
    @Transactional
    public Thread moveOnBehalf(UUID ticketId, UUID landlordId, UUID actorId, Ticket.Status to) {
        Ticket ticket = require(ticketId, landlordId, Role.LANDLORD);
        Ticket.Status from = ticket.getStatus();
        if (!movesFor(Role.LANDLORD, from).contains(to)) {
            throw ApiException.conflict("status_change",
                    "A request moves forward: acknowledged, in progress, then resolved. The tenant closes or reopens it.");
        }
        Instant at = next(ticket);
        ticket.move(to, at);
        events.save(TicketEvent.statusChange(ticket.getId(), actorId, from, to, at));
        publish(ticket, actorId, "status");
        return thread(ticket, landlordId, Role.LANDLORD);
    }

    /** For live subscriptions: only the request's landlord and tenant may listen to it. */
    @Transactional(readOnly = true)
    public boolean isParty(UUID ticketId, UUID userId) {
        return tickets.isParty(ticketId, userId);
    }

    private Ticket require(UUID ticketId, UUID userId, Role role) {
        return (role == Role.LANDLORD
                ? tickets.findByIdAndLandlordId(ticketId, userId)
                : tickets.findByIdAndTenantId(ticketId, userId))
                .orElseThrow(() -> ApiException.notFound("Request"));
    }

    private Thread thread(Ticket ticket, UUID userId, Role role) {
        LeaseService.LeaseView lease = leases.view(ticket.getLeaseId(), userId, role);
        List<EventView> timeline = eventViews(events.findByTicketIdOrderByCreatedAtAsc(ticket.getId()));
        return new Thread(view(ticket, lease), timeline, List.copyOf(movesFor(role, ticket.getStatus())));
    }

    private List<EventView> eventViews(List<TicketEvent> found) {
        Map<UUID, User> authors = users.findAllById(found.stream().map(TicketEvent::getAuthorId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, List<DocumentService.PhotoRef>> photos = documents.photosFor(found.stream().map(TicketEvent::getId)
                .toList());
        return found.stream().map(event -> {
            User author = authors.get(event.getAuthorId());
            return new EventView(event.getId(), event.getKind(), event.getBody(), event.getFromStatus(),
                    event.getToStatus(), event.getCreatedAt(),
                    new Author(event.getAuthorId(), author == null ? "Someone" : author.getFullName(),
                            author == null ? null : author.getRole()),
                    photos.getOrDefault(event.getId(), List.of()));
        }).toList();
    }

    /** Each line gets a later moment than the last, so the thread's order never ties. */
    private Instant next(Ticket ticket) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant after = ticket.getLastActivityAt().plus(1, ChronoUnit.MICROS);
        return now.isBefore(after) ? after : now;
    }

    private void publish(Ticket ticket, UUID actorId, String kind) {
        String actorName = users.findById(actorId).map(User::getFullName).orElse("Someone");
        publisher.publishEvent(new TicketChanged(ticket.getId(), ticket.getLeaseId(), ticket.getLandlordId(),
                ticket.getTenantId(), actorId, actorName, ticket.getTitle(), kind, ticket.getStatus(),
                ticket.getPriority()));
    }

    private static Set<Ticket.Status> movesFor(Role role, Ticket.Status from) {
        return (role == Role.LANDLORD ? LANDLORD_MOVES : TENANT_MOVES).getOrDefault(from, EnumSet.noneOf(Ticket.Status.class));
    }

    private static TicketView view(Ticket ticket, LeaseService.LeaseView lease) {
        return new TicketView(ticket.getId(), ticket.getLeaseId(), ticket.getTitle(), ticket.getCategory(),
                ticket.getPriority(), ticket.getStatus(), ticket.getCreatedAt(), ticket.getLastActivityAt(),
                lease.unit(), lease.property(), lease.tenant(), lease.landlord());
    }
}
