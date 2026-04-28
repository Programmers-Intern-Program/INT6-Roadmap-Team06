package com.back.coach.external.github;

public record GithubRepositoryMetadata(
        String nodeId,
        String fullName,
        String htmlUrl,
        String primaryLanguage,
        String defaultBranch
) {
}
