package com.back.coach.service.auth;

public record OAuthLoginResult(Long userId, String accessToken, String refreshToken) {
}
