package com.rentbook.payment;

import com.rentbook.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local development only (dev profile): attach a linked account created by hand in the Razorpay test
 * dashboard, for when Route onboarding through the API isn't enabled on the platform's test account.
 */
@RestController
@Profile("dev")
@PreAuthorize("hasRole('LANDLORD')")
class DevPayoutController {

    private final PayoutService payouts;

    DevPayoutController(PayoutService payouts) {
        this.payouts = payouts;
    }

    record LinkRequest(@Pattern(regexp = "^acc_[A-Za-z0-9]{6,20}$") @NotBlank String accountId,
                       @NotBlank String legalName) {
    }

    @PostMapping("/api/v1/payouts/account/link")
    String link(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LinkRequest body) {
        return payouts.linkExisting(CurrentUser.id(jwt), body.accountId(), body.legalName()).getActivationStatus();
    }
}
