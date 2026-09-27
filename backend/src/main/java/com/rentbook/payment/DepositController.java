package com.rentbook.payment;

import com.rentbook.common.CurrentUser;
import com.rentbook.user.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The deposit's settlement at move-out, read by both parties of the lease. */
@RestController
@RequestMapping("/api/v1/leases/{id}/deposit")
class DepositController {

    private final DepositSettlements deposits;

    DepositController(DepositSettlements deposits) {
        this.deposits = deposits;
    }

    record ProposeRequest(@NotNull @Size(max = 30) List<DepositSettlements.@Valid DeductionLine> deductions,
                          @Size(max = 500) String note) {
    }

    record QueryRequest(@Size(max = 500) String note) {
    }

    record RefundRequest(@NotNull Payment.Method method, @NotNull LocalDate refundedOn,
                         @Size(max = 200) String reference) {
    }

    @GetMapping
    DepositSettlements.DepositView view(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return deposits.view(id, CurrentUser.id(jwt), CurrentUser.isLandlord(jwt) ? Role.LANDLORD : Role.TENANT);
    }

    @PutMapping
    @PreAuthorize("hasRole('LANDLORD')")
    DepositSettlements.DepositView propose(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                           @Valid @RequestBody ProposeRequest body) {
        return deposits.propose(CurrentUser.id(jwt), id, body.deductions(), body.note());
    }

    @PostMapping("/accept")
    @PreAuthorize("hasRole('TENANT')")
    DepositSettlements.DepositView accept(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return deposits.accept(CurrentUser.id(jwt), id);
    }

    @PostMapping("/query")
    @PreAuthorize("hasRole('TENANT')")
    DepositSettlements.DepositView query(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                         @Valid @RequestBody QueryRequest body) {
        return deposits.query(CurrentUser.id(jwt), id, body.note());
    }

    @PostMapping("/refund")
    @PreAuthorize("hasRole('LANDLORD')")
    DepositSettlements.DepositView refund(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                          @Valid @RequestBody RefundRequest body) {
        return deposits.refund(CurrentUser.id(jwt), id, body.method(), body.refundedOn(), body.reference());
    }
}
