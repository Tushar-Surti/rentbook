package com.rentbook.lease;

import com.rentbook.common.CurrentUser;
import com.rentbook.user.Role;
import jakarta.validation.Valid;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/leases")
class LeaseController {

    private final LeaseService service;

    LeaseController(LeaseService service) {
        this.service = service;
    }

    record EndLeaseRequest(@NotNull LocalDate endsOn) {
    }

    @GetMapping
    List<LeaseService.LeaseView> list(@AuthenticationPrincipal Jwt jwt) {
        return service.visibleTo(CurrentUser.id(jwt), role(jwt));
    }

    @GetMapping("/{id}")
    LeaseService.LeaseView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.view(id, CurrentUser.id(jwt), role(jwt));
    }

    @PostMapping("/{id}/end")
    @PreAuthorize("hasRole('LANDLORD')")
    LeaseService.LeaseView end(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                               @Valid @RequestBody EndLeaseRequest body) {
        return service.end(id, CurrentUser.id(jwt), body.endsOn());
    }

    private static Role role(Jwt jwt) {
        return CurrentUser.isLandlord(jwt) ? Role.LANDLORD : Role.TENANT;
    }
}
