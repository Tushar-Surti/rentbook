package com.rentbook.maintenance;

import com.rentbook.common.CurrentUser;
import com.rentbook.user.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Both parties read and write a request's thread; only tenants open requests. Strangers get "not found". */
@RestController
@RequestMapping("/api/v1/tickets")
class TicketController {

    private final TicketService tickets;

    TicketController(TicketService tickets) {
        this.tickets = tickets;
    }

    record OpenRequest(@NotNull UUID leaseId, @NotBlank @Size(max = 140) String title,
                       @NotNull Ticket.Category category, @NotNull Ticket.Priority priority,
                       @NotBlank @Size(max = 4000) String body, @Size(max = 6) List<UUID> photoIds) {
    }

    record MessageRequest(@Size(max = 4000) String body, @Size(max = 6) List<UUID> photoIds) {
    }

    record StatusRequest(@NotNull Ticket.Status status) {
    }

    @GetMapping
    List<TicketService.TicketView> list(@AuthenticationPrincipal Jwt jwt,
                                        @RequestParam(required = false) UUID propertyId,
                                        @RequestParam(defaultValue = "false") boolean open) {
        return tickets.list(CurrentUser.id(jwt), role(jwt), propertyId, open);
    }

    @PostMapping
    @PreAuthorize("hasRole('TENANT')")
    @ResponseStatus(HttpStatus.CREATED)
    TicketService.Thread open(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OpenRequest body) {
        return tickets.open(CurrentUser.id(jwt), new TicketService.NewTicket(body.leaseId(), body.title(),
                body.category(), body.priority(), body.body(), body.photoIds()));
    }

    @GetMapping("/{id}")
    TicketService.Thread thread(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return tickets.thread(id, CurrentUser.id(jwt), role(jwt));
    }

    @PostMapping("/{id}/events")
    @ResponseStatus(HttpStatus.CREATED)
    TicketService.EventView post(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                 @Valid @RequestBody MessageRequest body) {
        return tickets.post(id, CurrentUser.id(jwt), role(jwt), body.body(), body.photoIds());
    }

    @PatchMapping("/{id}/status")
    TicketService.Thread status(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                @Valid @RequestBody StatusRequest body) {
        return tickets.move(id, CurrentUser.id(jwt), role(jwt), body.status());
    }

    private static Role role(Jwt jwt) {
        return CurrentUser.isLandlord(jwt) ? Role.LANDLORD : Role.TENANT;
    }
}
