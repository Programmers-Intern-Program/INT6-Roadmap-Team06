package com.back.coach.domain.github.dto;

import com.back.coach.domain.github.service.GithubConnectionService;

import java.time.Instant;

public record GithubConnectionResponse(
        String githubConnectionId,
        String githubLogin,
        Instant connectedAt
) {
    public static GithubConnectionResponse from(GithubConnectionService.ConnectResult result) {
        return new GithubConnectionResponse(
                String.valueOf(result.connectionId()),
                result.githubLogin(),
                result.connectedAt()
        );
    }
}
