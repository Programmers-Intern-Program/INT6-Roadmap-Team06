package com.back.coach.domain.github.dto;

import com.back.coach.domain.github.entity.GithubProject;

import java.util.List;

public record GithubRepositoryListResponse(
        String githubConnectionId,
        List<RepositoryItem> repositories
) {
    public record RepositoryItem(
            String repositoryId,
            String repoFullName,
            String repoUrl,
            String primaryLanguage,
            String defaultBranch
    ) {}

    public static GithubRepositoryListResponse from(Long connectionId, List<GithubProject> projects) {
        List<RepositoryItem> items = projects.stream()
                .map(p -> new RepositoryItem(
                        String.valueOf(p.getId()),
                        p.getRepoFullName(),
                        p.getRepoUrl(),
                        p.getPrimaryLanguage(),
                        p.getDefaultBranch()))
                .toList();
        return new GithubRepositoryListResponse(String.valueOf(connectionId), items);
    }
}
