package com.back.coach.service.github;

import java.util.List;
import java.util.Map;

// Slice 3 fetcher가 채우는 github_projects.metadata_payload JSONB의 역직렬화 record.
// 모든 컬렉션 필드는 누락 가능 (fetcher가 일부만 채울 수 있음).
public record RepoMetadata(
        String readmeExcerpt,
        Map<String, Long> languageBytes,
        List<DependencyFile> dependencyFiles,
        List<CommitItem> commits,
        List<PullRequestItem> pullRequests,
        List<IssueItem> issues
) {
    public record DependencyFile(String path, String contentExcerpt) {}

    public record CommitItem(
            String sha,
            String subject,
            String bodyExcerpt,
            List<String> paths,
            int additions,
            int deletions,
            String diffExcerpt
    ) {}

    public record PullRequestItem(
            int number,
            String title,
            String bodyExcerpt,
            String state,
            int additions,
            int deletions
    ) {}

    public record IssueItem(
            int number,
            String title,
            String bodyExcerpt,
            String state,
            List<String> commentExcerpts
    ) {}
}
