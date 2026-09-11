package com.rentbook.user;

import java.util.UUID;

public record MeResponse(UUID id, Role role, String fullName, String email, String phone) {

    public static MeResponse of(User user) {
        return new MeResponse(user.getId(), user.getRole(), user.getFullName(), user.getEmail(), user.getPhone());
    }
}
