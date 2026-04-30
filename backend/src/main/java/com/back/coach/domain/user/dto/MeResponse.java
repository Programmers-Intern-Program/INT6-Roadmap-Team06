package com.back.coach.domain.user.dto;

import com.back.coach.global.code.AuthProvider;

public record MeResponse(Long userId, String email, AuthProvider authProvider) {
}
