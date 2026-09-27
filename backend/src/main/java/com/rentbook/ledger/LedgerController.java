package com.rentbook.ledger;

import com.rentbook.common.CurrentUser;
import com.rentbook.user.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
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

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
class LedgerController {

    private final LedgerService ledger;

    LedgerController(LedgerService ledger) {
        this.ledger = ledger;
    }

    record AddChargeRequest(
            @NotNull Charge.Kind kind,
            @NotBlank @Size(max = 160) String description,
            @NotNull @Positive Long amountPaise,
            @NotNull LocalDate dueOn) {
    }

    @GetMapping("/leases/{id}/ledger")
    LedgerService.Ledger ledger(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ledger.ledger(id, CurrentUser.id(jwt), CurrentUser.isLandlord(jwt) ? Role.LANDLORD : Role.TENANT);
    }

    @PostMapping("/leases/{id}/charges")
    @PreAuthorize("hasRole('LANDLORD')")
    @ResponseStatus(HttpStatus.CREATED)
    LedgerService.Entry addCharge(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                  @Valid @RequestBody AddChargeRequest body) {
        return ledger.addCharge(CurrentUser.id(jwt), id, body.kind(), body.description(), body.amountPaise(),
                body.dueOn());
    }

    record AddonRequest(@NotNull Charge.Kind kind, @NotBlank @Size(max = 80) String label,
                        @NotNull @Positive Long amountPaise, @NotNull YearMonth startsMonth) {
    }

    @GetMapping("/leases/{id}/addons")
    List<LedgerService.Addon> addons(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ledger.addons(id, CurrentUser.id(jwt), CurrentUser.isLandlord(jwt) ? Role.LANDLORD : Role.TENANT);
    }

    @PostMapping("/leases/{id}/addons")
    @PreAuthorize("hasRole('LANDLORD')")
    @ResponseStatus(HttpStatus.CREATED)
    LedgerService.Addon addAddon(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                 @Valid @RequestBody AddonRequest body) {
        return ledger.addAddon(CurrentUser.id(jwt), id, body.kind(), body.label(), body.amountPaise(), body.startsMonth());
    }

    @PostMapping("/addons/{id}/stop")
    @PreAuthorize("hasRole('LANDLORD')")
    LedgerService.Addon stopAddon(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ledger.stopAddon(CurrentUser.id(jwt), id);
    }

    @PostMapping("/charges/{id}/waive")
    @PreAuthorize("hasRole('LANDLORD')")
    LedgerService.Entry waive(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ledger.waive(CurrentUser.id(jwt), id);
    }
}
