package com.rentbook.dashboard;

import com.rentbook.common.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
class DashboardController {

    private final DashboardService service;

    DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/landlord")
    @PreAuthorize("hasRole('LANDLORD')")
    DashboardService.LandlordBoard landlord(@AuthenticationPrincipal Jwt jwt) {
        return service.landlord(CurrentUser.id(jwt));
    }

    @GetMapping("/tenant")
    @PreAuthorize("hasRole('TENANT')")
    DashboardService.TenantHome tenant(@AuthenticationPrincipal Jwt jwt) {
        return service.tenant(CurrentUser.id(jwt));
    }
}
