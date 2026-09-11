package com.rentbook.payment;

import com.rentbook.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@PreAuthorize("hasRole('TENANT')")
class PaymentController {

    private final CheckoutService checkout;

    PaymentController(CheckoutService checkout) {
        this.checkout = checkout;
    }

    /** An empty {@code chargeIds} means every unpaid charge on the lease. */
    record CheckoutRequest(@NotNull UUID leaseId, List<UUID> chargeIds) {
    }

    /** Razorpay Checkout's handler response, renamed from its snake_case. */
    record ClientCallback(@NotBlank String razorpayOrderId, @NotBlank String razorpayPaymentId,
                          @NotBlank String razorpaySignature) {
    }

    record PaymentView(UUID id, Payment.Status status, long amountPaise, Instant capturedAt, String failureReason) {
        static PaymentView of(Payment payment) {
            return new PaymentView(payment.getId(), payment.getStatus(), payment.getAmountPaise(),
                    payment.getCapturedAt(), payment.getFailureReason());
        }
    }

    @PostMapping("/checkout")
    CheckoutService.Checkout start(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CheckoutRequest body) {
        return checkout.start(CurrentUser.id(jwt), body.leaseId(), body.chargeIds());
    }

    @PostMapping("/{id}/client-callback")
    PaymentView clientCallback(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                               @Valid @RequestBody ClientCallback body) {
        return PaymentView.of(checkout.browserSaysPaid(CurrentUser.id(jwt), id, body.razorpayOrderId(),
                body.razorpayPaymentId(), body.razorpaySignature()));
    }

    @GetMapping("/{id}")
    PaymentView status(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return PaymentView.of(checkout.status(CurrentUser.id(jwt), id));
    }
}
