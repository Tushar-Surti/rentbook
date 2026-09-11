package com.rentbook.payment;

import com.rentbook.common.CurrentUser;
import com.rentbook.user.Role;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Both parties of a lease read its receipts; anyone else gets "not found". */
@RestController
@RequestMapping("/api/v1")
class ReceiptController {

    private final ReceiptService receipts;

    ReceiptController(ReceiptService receipts) {
        this.receipts = receipts;
    }

    @GetMapping("/leases/{id}/receipts")
    List<ReceiptService.ReceiptView> forLease(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return receipts.forLease(id, CurrentUser.id(jwt), role(jwt));
    }

    @GetMapping("/receipts/{id}/pdf")
    ResponseEntity<byte[]> pdf(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        ReceiptService.ReceiptFile file = receipts.pdf(id, CurrentUser.id(jwt), role(jwt));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename()).build().toString())
                .body(file.pdf());
    }

    private static Role role(Jwt jwt) {
        return CurrentUser.isLandlord(jwt) ? Role.LANDLORD : Role.TENANT;
    }
}
