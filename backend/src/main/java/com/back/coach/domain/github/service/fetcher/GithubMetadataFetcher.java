package com.back.coach.domain.github.service.fetcher;

import com.back.coach.domain.github.service.RepoMetadata;
import com.back.coach.external.github.GithubApiClient;
import com.back.coach.external.github.dto.*;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class GithubMetadataFetcher {

    public static final int README_MAX_BYTES = 1024;
    public static final int DEP_FILE_MAX_BYTES = 2048;
    static final int BODY_MAX_CHARS = 500;
    static final int COMMIT_LIMIT = 5;

    private static final List<String> DEP_FILE_PATHS = List.of(
            "pom.xml", "build.gradle", "build.gradle.kts", "package.json",
            "requirements.txt", "pyproject.toml", "Cargo.toml", "go.mod", "Gemfile"
    );

    private final GithubApiClient apiClient;

    public GithubMetadataFetcher(GithubApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public RepoMetadata fetch(String accessToken, String owner, String repo, String githubLogin) {
        String readmeExcerpt = fetchReadme(accessToken, owner, repo);
        Map<String, Long> languageBytes = apiClient.getLanguages(accessToken, owner, repo);
        List<RepoMetadata.DependencyFile> depFiles = fetchDepFiles(accessToken, owner, repo);
        List<RepoMetadata.CommitItem> commits = fetchCommits(accessToken, owner, repo, githubLogin);
        List<RepoMetadata.PullRequestItem> prs = fetchPullRequests(accessToken, owner, repo, githubLogin);
        List<RepoMetadata.IssueItem> issues = fetchIssues(accessToken, owner, repo, githubLogin);

        return new RepoMetadata(readmeExcerpt, languageBytes, depFiles, commits, prs, issues);
    }

    private String fetchReadme(String token, String owner, String repo) {
        return apiClient.getReadme(token, owner, repo)
                .map(raw -> truncateBytes(raw, README_MAX_BYTES))
                .orElse(null);
    }

    private List<RepoMetadata.DependencyFile> fetchDepFiles(String token, String owner, String repo) {
        List<RepoMetadata.DependencyFile> result = new ArrayList<>();
        for (String path : DEP_FILE_PATHS) {
            apiClient.getFileContent(token, owner, repo, path).ifPresent(content -> {
                String excerpt = truncateBytes(content, DEP_FILE_MAX_BYTES);
                result.add(new RepoMetadata.DependencyFile(path, excerpt));
            });
        }
        return result;
    }

    private List<RepoMetadata.CommitItem> fetchCommits(String token, String owner, String repo, String login) {
        List<GithubCommitDto> commits = apiClient.listCommits(token, owner, repo, login, COMMIT_LIMIT);
        // GitHub returns newest-first; reverse to oldest-first
        List<GithubCommitDto> ascending = new ArrayList<>(commits);
        Collections.reverse(ascending);

        List<RepoMetadata.CommitItem> result = new ArrayList<>();
        for (GithubCommitDto commit : ascending) {
            GithubCommitDetailDto detail = apiClient.getCommitDetail(token, owner, repo, commit.sha());
            result.add(toCommitItem(detail));
        }
        return result;
    }

    private static RepoMetadata.CommitItem toCommitItem(GithubCommitDetailDto detail) {
        String message = detail.commit() != null ? detail.commit().message() : "";
        String[] lines = message == null ? new String[]{""} : message.split("\n", 2);
        String subject = lines[0].trim();
        String body = lines.length > 1 ? truncateChars(lines[1].trim(), BODY_MAX_CHARS) : null;

        List<String> paths = detail.files() == null ? List.of() :
                detail.files().stream().map(GithubCommitDetailDto.FileChange::filename).toList();

        int additions = detail.stats() != null ? detail.stats().additions() : 0;
        int deletions = detail.stats() != null ? detail.stats().deletions() : 0;

        String diffExcerpt = buildDiffExcerpt(detail);

        return new RepoMetadata.CommitItem(detail.sha(), subject, body, paths, additions, deletions, diffExcerpt);
    }

    private static String buildDiffExcerpt(GithubCommitDetailDto detail) {
        if (detail.files() == null || detail.files().isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (GithubCommitDetailDto.FileChange f : detail.files()) {
            if (f.patch() == null) continue;
            sb.append("diff --git a/").append(f.filename()).append(" b/").append(f.filename()).append('\n');
            sb.append(f.patch()).append('\n');
            if (sb.length() >= 8192) break;
        }
        String raw = sb.toString();
        if (raw.isBlank()) return null;
        return raw.length() > 8192 ? raw.substring(0, 8192) : raw;
    }

    private List<RepoMetadata.PullRequestItem> fetchPullRequests(String token, String owner, String repo,
                                                                    String login) {
        return apiClient.listPullRequests(token, owner, repo).stream()
                .filter(pr -> pr.user() != null && login.equals(pr.user().login()))
                .sorted(Comparator.comparing(GithubPrDto::createdAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(pr -> new RepoMetadata.PullRequestItem(
                        pr.number(),
                        pr.title(),
                        truncateChars(pr.body(), BODY_MAX_CHARS),
                        pr.state() != null ? pr.state().toUpperCase() : "OPEN",
                        pr.additions(),
                        pr.deletions()))
                .toList();
    }

    private List<RepoMetadata.IssueItem> fetchIssues(String token, String owner, String repo, String login) {
        return apiClient.listIssues(token, owner, repo).stream()
                .filter(i -> !i.isPullRequest())
                .filter(i -> i.user() != null && login.equals(i.user().login()))
                .sorted(Comparator.comparing(GithubIssueDto::createdAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(i -> new RepoMetadata.IssueItem(
                        i.number(),
                        i.title(),
                        truncateChars(i.body(), BODY_MAX_CHARS),
                        i.state() != null ? i.state().toUpperCase() : "OPEN",
                        List.of()))
                .toList();
    }

    static String truncateBytes(String text, int maxBytes) {
        if (text == null) return null;
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) return text;
        String marker = "[…truncated]";
        String truncated = new String(bytes, 0, maxBytes, StandardCharsets.UTF_8);
        // Remove potentially incomplete multi-byte char at boundary
        while (truncated.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            truncated = truncated.substring(0, truncated.length() - 1);
        }
        return truncated + marker;
    }

    static String truncateChars(String text, int maxChars) {
        if (text == null) return null;
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }
}
