package com.rentbook.payment;

import com.rentbook.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** A landlord records rent they were paid directly: cash, UPI, a bank transfer or a cheque. */
@RestController
@RequestMapping("/api/v1")
class RecordedPaymentController {

    private final RecordedPayments recorded;

    RecordedPaymentController(RecordedPayments recorded) {
        this.recorded = recorded;
    }

    record RecordRequest(
            @NotEmpty @Size(max = 50) List<@NotNull UUID> chargeIds,
            @NotNull Payment.Method method,
            @NotNull LocalDate receivedOn,
            @Size(max = 200) String note) {
    }

    @PostMapping("/leases/{id}/payments")
    @PreAuthorize("hasRole('LANDLORD')")
    @ResponseStatus(HttpStatus.CREATED)
    RecordedPayments.Recorded record(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                     @Valid @RequestBody RecordRequest body) {
        return recorded.record(CurrentUser.id(jwt), id, body.chargeIds(), body.method(), body.receivedOn(), body.note());
    }
}
