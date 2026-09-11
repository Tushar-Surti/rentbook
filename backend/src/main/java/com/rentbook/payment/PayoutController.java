package com.rentbook.payment;

import com.rentbook.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payouts")
@PreAuthorize("hasRole('LANDLORD')")
class PayoutController {

    private final PayoutService payouts;
    private final RazorpayProperties razorpay;

    PayoutController(PayoutService payouts, RazorpayProperties razorpay) {
        this.payouts = payouts;
        this.razorpay = razorpay;
    }

    record OnboardRequest(
            @NotBlank @Size(min = 4, max = 200) String legalName,
            @Pattern(regexp = "^([A-Z]{5}[0-9]{4}[A-Z])?$", message = "Enter a PAN like ABCDE1234F") String pan,
            @Pattern(regexp = "^(\\+91[6-9][0-9]{9})?$", message = "Enter a 10-digit Indian mobile number") String phone,
            @NotBlank @Size(max = 200) String street,
            @NotBlank @Size(max = 80) String city,
            @NotBlank @Size(max = 80) String state,
            @Pattern(regexp = "^[1-9][0-9]{5}$", message = "Enter a 6-digit PIN code") String postalCode,
            @Pattern(regexp = "^[0-9]{9,18}$", message = "Enter the account number, digits only") String accountNumber,
            @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", message = "Enter an IFSC like HDFC0001234") String ifsc,
            @NotBlank @Size(max = 120) String beneficiaryName) {
    }

    /** Never includes the account number; only its last four digits are stored. */
    record PayoutView(boolean paymentsConfigured, String status, boolean active, String legalName,
                      String beneficiaryName, String ifsc, String bankLast4, int platformFeeBps) {
        static PayoutView of(PayoutAccount account, boolean configured, int feeBps) {
            return new PayoutView(configured, account.getActivationStatus(), account.isActive(),
                    account.getLegalName(), account.getBeneficiaryName(), account.getIfsc(), account.getBankLast4(),
                    feeBps);
        }
    }

    @GetMapping("/account")
    PayoutView account(@AuthenticationPrincipal Jwt jwt) {
        UUID landlordId = CurrentUser.id(jwt);
        int feeBps = payouts.feeBps(landlordId);
        return payouts.account(landlordId)
                .map(account -> PayoutView.of(account, razorpay.configured(), feeBps))
                .orElseGet(() -> new PayoutView(razorpay.configured(), "NOT_STARTED", false, null, null, null, null,
                        feeBps));
    }

    @PostMapping("/account")
    PayoutView onboard(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OnboardRequest body) {
        UUID landlordId = CurrentUser.id(jwt);
        PayoutAccount account = payouts.onboard(landlordId, new PayoutService.Onboarding(body.legalName(),
                blankToNull(body.pan()), blankToNull(body.phone()), body.street(), body.city(), body.state(),
                body.postalCode(), body.accountNumber(), body.ifsc(), body.beneficiaryName()));
        return PayoutView.of(account, razorpay.configured(), payouts.feeBps(landlordId));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
