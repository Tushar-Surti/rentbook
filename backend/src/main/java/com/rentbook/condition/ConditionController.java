package com.rentbook.condition;

import com.rentbook.common.CurrentUser;
import com.rentbook.document.DocumentService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Move-in and move-out condition reports: the landlord writes and sends, the tenant notes and confirms. */
@RestController
@RequestMapping("/api/v1")
class ConditionController {

    private final ConditionReports reports;

    ConditionController(ConditionReports reports) {
        this.reports = reports;
    }

    record NewReport(@NotNull ConditionReport.Kind kind) {
    }

    record NewLine(@NotBlank @Size(max = 60) String area, @NotBlank @Size(max = 80) String item) {
    }

    record LineUpdate(@NotNull ConditionItem.Condition condition, @Size(max = 500) String note) {
    }

    record TenantNote(@NotNull UUID lineId, @Size(max = 500) String note) {
    }

    record Confirmation(List<@Valid TenantNote> notes) {
    }

    record PhotoUpload(@NotBlank @Size(max = 200) String filename, @NotBlank @Size(max = 100) String contentType,
                       @NotNull @Positive Long sizeBytes) {
    }

    @GetMapping("/leases/{leaseId}/condition-reports")
    List<ConditionReports.Summary> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID leaseId) {
        return reports.list(leaseId, CurrentUser.id(jwt), role(jwt));
    }

    @PostMapping("/leases/{leaseId}/condition-reports")
    @PreAuthorize("hasRole('LANDLORD')")
    @ResponseStatus(HttpStatus.CREATED)
    ConditionReports.ReportView create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID leaseId,
                                       @Valid @RequestBody NewReport body) {
        return reports.create(CurrentUser.id(jwt), leaseId, body.kind());
    }

    @GetMapping("/condition-reports/{id}")
    ConditionReports.ReportView view(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return reports.view(id, CurrentUser.id(jwt), role(jwt));
    }

    @DeleteMapping("/condition-reports/{id}")
    @PreAuthorize("hasRole('LANDLORD')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void discard(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        reports.discard(CurrentUser.id(jwt), id);
    }

    @PostMapping("/condition-reports/{id}/lines")
    @PreAuthorize("hasRole('LANDLORD')")
    @ResponseStatus(HttpStatus.CREATED)
    ConditionReports.ReportView addLine(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                        @Valid @RequestBody NewLine body) {
        return reports.addLine(CurrentUser.id(jwt), id, body.area(), body.item());
    }

    @PatchMapping("/condition-lines/{id}")
    @PreAuthorize("hasRole('LANDLORD')")
    ConditionReports.Line updateLine(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                     @Valid @RequestBody LineUpdate body) {
        return reports.updateLine(CurrentUser.id(jwt), id, body.condition(), body.note());
    }

    @DeleteMapping("/condition-lines/{id}")
    @PreAuthorize("hasRole('LANDLORD')")
    ConditionReports.ReportView removeLine(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return reports.removeLine(CurrentUser.id(jwt), id);
    }

    @PostMapping("/condition-reports/{id}/send")
    @PreAuthorize("hasRole('LANDLORD')")
    ConditionReports.ReportView send(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return reports.send(CurrentUser.id(jwt), id);
    }

    @PostMapping("/condition-reports/{id}/confirm")
    @PreAuthorize("hasRole('TENANT')")
    ConditionReports.ReportView confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                        @Valid @RequestBody Confirmation body) {
        Map<UUID, String> notes = new LinkedHashMap<>();
        if (body.notes() != null) {
            body.notes().forEach(note -> notes.put(note.lineId(), note.note()));
        }
        return reports.confirm(CurrentUser.id(jwt), id, notes);
    }

    @PostMapping("/condition-lines/{id}/photos")
    @ResponseStatus(HttpStatus.CREATED)
    DocumentService.UploadTicket photo(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                       @Valid @RequestBody PhotoUpload body) {
        return reports.startPhoto(CurrentUser.id(jwt), role(jwt), id, body.filename(), body.contentType(),
                body.sizeBytes());
    }

    @DeleteMapping("/condition-photos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removePhoto(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        reports.removePhoto(CurrentUser.id(jwt), role(jwt), id);
    }

    private static Role role(Jwt jwt) {
        return CurrentUser.isLandlord(jwt) ? Role.LANDLORD : Role.TENANT;
    }
}
