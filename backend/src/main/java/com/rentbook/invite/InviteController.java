package com.rentbook.invite;

import com.rentbook.auth.SessionCookies;
import com.rentbook.common.CurrentUser;
import com.rentbook.property.PropertyRepository;
import com.rentbook.property.Unit;
import com.rentbook.property.UnitRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
class InviteController {

    private static final String PHONE = "^\\+?[1-9][0-9]{9,14}$";

    private final InviteService service;
    private final SessionCookies cookies;
    private final UnitRepository units;
    private final PropertyRepository properties;
    private final Clock clock;

    InviteController(InviteService service, SessionCookies cookies, UnitRepository units,
                     PropertyRepository properties, Clock clock) {
        this.service = service;
        this.cookies = cookies;
        this.units = units;
        this.properties = properties;
        this.clock = clock;
    }

    record CreateInviteRequest(
            @NotBlank @Size(max = 120) String tenantName,
            @NotBlank @Email @Size(max = 254) String email,
            @Pattern(regexp = PHONE, message = "Enter a mobile number with country code") String phone,
            @NotNull @Positive Long rentPaise,
            @NotNull @PositiveOrZero Long depositPaise,
            @NotNull @Min(1) @Max(28) Integer dueDay,
            @NotNull LocalDate startsOn,
            LocalDate endsOn) {
    }

    record AcceptInviteRequest(
            @Size(max = 120) String fullName,
            @Pattern(regexp = PHONE, message = "Enter a mobile number with country code") String phone,
            @NotBlank @Size(max = 72) String password) {
    }

    record InviteResponse(UUID id, UUID unitId, String unitLabel, String propertyName, String tenantName,
                          String email, String phone, long rentPaise, long depositPaise, int dueDay,
                          LocalDate startsOn, LocalDate endsOn, Invite.Status status, Instant expiresAt) {
    }

    /** The link is returned once so the landlord can also share it directly (WhatsApp, SMS). */
    record IssuedInviteResponse(InviteResponse invite, URI link) {
    }

    @PostMapping("/units/{unitId}/invites")
    @PreAuthorize("hasRole('LANDLORD')")
    @ResponseStatus(HttpStatus.CREATED)
    IssuedInviteResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID unitId,
                                @Valid @RequestBody CreateInviteRequest body) {
        InviteService.Issued issued = service.create(CurrentUser.id(jwt), unitId, new InviteService.Terms(
                body.tenantName(), body.email(), body.phone(), body.rentPaise(), body.depositPaise(), body.dueDay(),
                body.startsOn(), body.endsOn()));
        return new IssuedInviteResponse(view(issued.invite()), issued.link());
    }

    @GetMapping("/invites")
    @PreAuthorize("hasRole('LANDLORD')")
    List<InviteResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(CurrentUser.id(jwt)).stream().map(this::view).toList();
    }

    @PostMapping("/invites/{id}/resend")
    @PreAuthorize("hasRole('LANDLORD')")
    IssuedInviteResponse resend(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        InviteService.Issued issued = service.resend(CurrentUser.id(jwt), id);
        return new IssuedInviteResponse(view(issued.invite()), issued.link());
    }

    @PostMapping("/invites/{id}/revoke")
    @PreAuthorize("hasRole('LANDLORD')")
    InviteResponse revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return view(service.revoke(CurrentUser.id(jwt), id));
    }

    @GetMapping("/invites/{token}")
    InviteService.Preview preview(@PathVariable String token) {
        return service.preview(token);
    }

    @PostMapping("/invites/{token}/accept")
    ResponseEntity<SessionCookies.SessionResponse> accept(@PathVariable String token,
                                                          @Valid @RequestBody AcceptInviteRequest body) {
        return cookies.respond(service.accept(token, body.fullName(), body.phone(), body.password()),
                HttpStatus.CREATED);
    }

    private InviteResponse view(Invite invite) {
        Unit unit = units.findById(invite.getUnitId()).orElseThrow();
        String propertyName = properties.findById(unit.getPropertyId()).map(p -> p.getName()).orElse("");
        return new InviteResponse(invite.getId(), unit.getId(), unit.getLabel(), propertyName, invite.getTenantName(),
                invite.getEmail(), invite.getPhone(), invite.getRentPaise(), invite.getDepositPaise(),
                invite.getDueDay(), invite.getStartsOn(), invite.getEndsOn(),
                invite.effectiveStatus(clock.instant()), invite.getExpiresAt());
    }
}
