package com.back.coach.service.auth;

public record GithubUserInfo(String providerUserId, String login, String email) {
}
