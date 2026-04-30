package com.back.coach.domain.user.service;

public record OAuthLoginResult(Long userId, String accessToken, String refreshToken) {
}
