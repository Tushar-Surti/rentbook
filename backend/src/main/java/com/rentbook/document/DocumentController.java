package com.rentbook.document;

import com.rentbook.common.CurrentUser;
import com.rentbook.user.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Uploads go straight to storage: these endpoints sign them, confirm them, list a lease's shelf and hand
 * out short-lived links. Strangers to a lease get "not found".
 */
@RestController
@RequestMapping("/api/v1")
class DocumentController {

    private final DocumentService documents;

    DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    record UploadBody(@NotNull UUID leaseId, @NotNull Document.Type type, @NotBlank @Size(max = 200) String filename,
                      @NotBlank @Size(max = 100) String contentType, @Positive long sizeBytes,
                      Document.Visibility visibility) {
    }

    @PostMapping("/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    DocumentService.UploadTicket upload(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UploadBody body) {
        return documents.startUpload(CurrentUser.id(jwt), role(jwt), new DocumentService.UploadRequest(body.leaseId(),
                body.type(), body.filename(), body.contentType(), body.sizeBytes(), body.visibility()));
    }

    @PostMapping("/documents/{id}/complete")
    DocumentService.DocumentView complete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return documents.complete(id, CurrentUser.id(jwt));
    }

    @GetMapping("/documents/{id}/download")
    DocumentService.Link download(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                  @RequestParam(defaultValue = "false") boolean attachment) {
        return documents.download(id, CurrentUser.id(jwt), role(jwt), attachment);
    }

    @GetMapping("/leases/{leaseId}/documents")
    List<DocumentService.VaultEntry> vault(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID leaseId) {
        return documents.vault(leaseId, CurrentUser.id(jwt), role(jwt));
    }

    @DeleteMapping("/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        documents.remove(id, CurrentUser.id(jwt));
    }

    private static Role role(Jwt jwt) {
        return CurrentUser.isLandlord(jwt) ? Role.LANDLORD : Role.TENANT;
    }
}
