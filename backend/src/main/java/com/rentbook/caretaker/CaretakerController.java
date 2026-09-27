package com.rentbook.caretaker;

import com.rentbook.auth.SessionCookies;
import com.rentbook.common.CurrentUser;
import com.rentbook.ledger.LedgerService;
import com.rentbook.lease.LeaseService;
import com.rentbook.maintenance.Ticket;
import com.rentbook.maintenance.TicketService;
import com.rentbook.payment.Payment;
import com.rentbook.payment.RecordedPayments;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Three doors: the landlord manages caretakers ({@code /caretakers}), an invitee joins from their link
 * ({@code /caretaker-invites}), and a caretaker works ({@code /caretaker}). The caretaker's door is narrow on
 * purpose: nothing here reaches documents, payouts, invites, charges, waivers or the deposit.
 */
@RestController
@RequestMapping("/api/v1")
class CaretakerController {

    private final Caretakers caretakers;
    private final CaretakerWork work;
    private final SessionCookies cookies;

    CaretakerController(Caretakers caretakers, CaretakerWork work, SessionCookies cookies) {
        this.caretakers = caretakers;
        this.work = work;
        this.cookies = cookies;
    }

    static final String PHONE_PATTERN = "^\\+?[1-9][0-9]{9,14}$";

    record InviteRequest(@NotBlank @Size(max = 120) String fullName, @NotBlank @Email @Size(max = 254) String email,
                         @Pattern(regexp = PHONE_PATTERN, message = "Enter a mobile number with country code") String phone,
                         @NotEmpty List<@NotNull UUID> propertyIds) {
    }

    record AssignRequest(@NotEmpty List<@NotNull UUID> propertyIds) {
    }

    record AcceptRequest(@Size(max = 120) String fullName,
                         @NotBlank @Size(min = 8, max = 72, message = "Use 8 to 72 characters") String password) {
    }

    record RecordRequest(@NotEmpty @Size(max = 50) List<@NotNull UUID> chargeIds, @NotNull Payment.Method method,
                         @NotNull LocalDate receivedOn, @Size(max = 200) String note) {
    }

    record PostRequest(@NotBlank @Size(max = 4000) String body) {
    }

    record MoveRequest(@NotNull Ticket.Status status) {
    }

    // The landlord's door.

    @GetMapping("/caretakers")
    @PreAuthorize("hasRole('LANDLORD')")
    List<Caretakers.CaretakerView> list(@AuthenticationPrincipal Jwt jwt) {
        return caretakers.list(CurrentUser.id(jwt));
    }

    @PostMapping("/caretakers")
    @PreAuthorize("hasRole('LANDLORD')")
    @ResponseStatus(HttpStatus.CREATED)
    Caretakers.Issued invite(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody InviteRequest body) {
        return caretakers.invite(CurrentUser.id(jwt), body.fullName(), body.email(), body.phone(), body.propertyIds());
    }

    @PutMapping("/caretakers/{id}/properties")
    @PreAuthorize("hasRole('LANDLORD')")
    Caretakers.CaretakerView assign(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                    @Valid @RequestBody AssignRequest body) {
        return caretakers.assign(CurrentUser.id(jwt), id, body.propertyIds());
    }

    @PostMapping("/caretakers/{id}/resend")
    @PreAuthorize("hasRole('LANDLORD')")
    Caretakers.Issued resend(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return caretakers.resend(CurrentUser.id(jwt), id);
    }

    @DeleteMapping("/caretakers/{id}")
    @PreAuthorize("hasRole('LANDLORD')")
    ResponseEntity<Void> remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        caretakers.remove(CurrentUser.id(jwt), id);
        return ResponseEntity.noContent().build();
    }

    // The invitee's door.

    @GetMapping("/caretaker-invites/{token}")
    Caretakers.Preview preview(@PathVariable String token) {
        return caretakers.preview(token);
    }

    @PostMapping("/caretaker-invites/{token}/accept")
    ResponseEntity<SessionCookies.SessionResponse> accept(@PathVariable String token,
                                                          @Valid @RequestBody AcceptRequest body) {
        return cookies.respond(caretakers.accept(token, body.fullName(), body.password()), HttpStatus.CREATED);
    }

    // The caretaker's door.

    @GetMapping("/caretaker/board")
    @PreAuthorize("hasRole('CARETAKER')")
    CaretakerWork.Board board(@AuthenticationPrincipal Jwt jwt) {
        return work.board(CurrentUser.id(jwt));
    }

    @GetMapping("/caretaker/leases/{id}")
    @PreAuthorize("hasRole('CARETAKER')")
    LeaseService.LeaseView lease(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return work.lease(CurrentUser.id(jwt), id);
    }

    @GetMapping("/caretaker/leases/{id}/ledger")
    @PreAuthorize("hasRole('CARETAKER')")
    LedgerService.Ledger ledger(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return work.ledger(CurrentUser.id(jwt), id);
    }

    @PostMapping("/caretaker/leases/{id}/payments")
    @PreAuthorize("hasRole('CARETAKER')")
    @ResponseStatus(HttpStatus.CREATED)
    RecordedPayments.Recorded record(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                     @Valid @RequestBody RecordRequest body) {
        return work.record(CurrentUser.id(jwt), id, body.chargeIds(), body.method(), body.receivedOn(), body.note());
    }

    @GetMapping("/caretaker/tickets")
    @PreAuthorize("hasRole('CARETAKER')")
    List<TicketService.TicketView> tickets(@AuthenticationPrincipal Jwt jwt,
                                           @RequestParam(required = false) UUID propertyId,
                                           @RequestParam(defaultValue = "false") boolean open) {
        return work.tickets(CurrentUser.id(jwt), propertyId, open);
    }

    @GetMapping("/caretaker/tickets/{id}")
    @PreAuthorize("hasRole('CARETAKER')")
    TicketService.Thread thread(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return work.thread(CurrentUser.id(jwt), id);
    }

    @PostMapping("/caretaker/tickets/{id}/events")
    @PreAuthorize("hasRole('CARETAKER')")
    @ResponseStatus(HttpStatus.CREATED)
    TicketService.EventView post(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                 @Valid @RequestBody PostRequest body) {
        return work.post(CurrentUser.id(jwt), id, body.body());
    }

    @PatchMapping("/caretaker/tickets/{id}/status")
    @PreAuthorize("hasRole('CARETAKER')")
    TicketService.Thread move(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                              @Valid @RequestBody MoveRequest body) {
        return work.move(CurrentUser.id(jwt), id, body.status());
    }
}
