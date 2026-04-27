package com.back.coach.global.security;

import java.security.Principal;

public record AuthenticatedUser(Long userId) implements Principal {

    public AuthenticatedUser {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
    }

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
