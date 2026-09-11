package com.rentbook.user;

import com.rentbook.common.ApiException;
import com.rentbook.common.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController {

    private final UserRepository users;

    MeController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/api/v1/me")
    MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return users.findById(CurrentUser.id(jwt)).map(MeResponse::of).orElseThrow(() -> ApiException.notFound("User"));
    }
}
