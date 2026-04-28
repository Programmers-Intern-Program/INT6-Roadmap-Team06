package com.back.coach.api.auth.dto;

import com.back.coach.global.code.AuthProvider;

public record MeResponse(Long userId, String email, AuthProvider authProvider) {
}
