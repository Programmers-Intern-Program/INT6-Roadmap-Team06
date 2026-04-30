package com.back.coach.domain.github.service.fetcher;

import com.back.coach.domain.github.service.RepoMetadata;
import com.back.coach.external.github.GithubApiClient;
import com.back.coach.external.github.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class GithubMetadataFetcherTest {

    GithubApiClient apiClient;
    GithubMetadataFetcher fetcher;

    static final String TOKEN = "ghp_token";
    static final String OWNER = "user";
    static final String REPO = "my-repo";
    static final String LOGIN = "user";

    @BeforeEach
    void setUp() {
        apiClient = mock(GithubApiClient.class);
        fetcher = new GithubMetadataFetcher(apiClient);
        // default stubs — can be overridden in individual tests
        given(apiClient.getReadme(TOKEN, OWNER, REPO)).willReturn(Optional.of("# Hello"));
        given(apiClient.getLanguages(TOKEN, OWNER, REPO)).willReturn(Map.of("Java", 10000L));
        given(apiClient.getFileContent(eq(TOKEN), eq(OWNER), eq(REPO), anyString()))
                .willReturn(Optional.empty());
        given(apiClient.listCommits(TOKEN, OWNER, REPO, LOGIN, 20)).willReturn(List.of());
        given(apiClient.listPullRequests(TOKEN, OWNER, REPO)).willReturn(List.of());
        given(apiClient.listIssues(TOKEN, OWNER, REPO)).willReturn(List.of());
    }

    @Test
    @DisplayName("readme가 1KB를 초과하면 잘라내고 마커 추가")
    void fetch_readmeTruncatedAt1KB() {
        String longReadme = "A".repeat(2000);
        given(apiClient.getReadme(TOKEN, OWNER, REPO)).willReturn(Optional.of(longReadme));

        RepoMetadata metadata = fetcher.fetch(TOKEN, OWNER, REPO, LOGIN);

        assertThat(metadata.readmeExcerpt()).hasSizeLessThanOrEqualTo(GithubMetadataFetcher.README_MAX_BYTES + 20);
        assertThat(metadata.readmeExcerpt()).endsWith("[…truncated]");
    }

    @Test
    @DisplayName("readme가 없으면 null")
    void fetch_noReadme_null() {
        given(apiClient.getReadme(TOKEN, OWNER, REPO)).willReturn(Optional.empty());

        RepoMetadata metadata = fetcher.fetch(TOKEN, OWNER, REPO, LOGIN);

        assertThat(metadata.readmeExcerpt()).isNull();
    }

    @Test
    @DisplayName("dependencyFiles — 존재하는 파일만 포함, 없는 파일 skip")
    void fetch_depFiles_onlyExisting() {
        given(apiClient.getFileContent(TOKEN, OWNER, REPO, "pom.xml"))
                .willReturn(Optional.of("<project/>"));

        RepoMetadata metadata = fetcher.fetch(TOKEN, OWNER, REPO, LOGIN);

        assertThat(metadata.dependencyFiles()).hasSize(1);
        assertThat(metadata.dependencyFiles().get(0).path()).isEqualTo("pom.xml");
        assertThat(metadata.dependencyFiles().get(0).contentExcerpt()).isEqualTo("<project/>");
    }

    @Test
    @DisplayName("dependencyFiles — contentExcerpt 2KB 초과 시 잘라냄")
    void fetch_depFilesContentTruncatedAt2KB() {
        String bigContent = "x".repeat(5000);
        given(apiClient.getFileContent(TOKEN, OWNER, REPO, "pom.xml")).willReturn(Optional.of(bigContent));

        RepoMetadata metadata = fetcher.fetch(TOKEN, OWNER, REPO, LOGIN);

        String excerpt = metadata.dependencyFiles().get(0).contentExcerpt();
        assertThat(excerpt.getBytes().length).isLessThanOrEqualTo(GithubMetadataFetcher.DEP_FILE_MAX_BYTES + 20);
        assertThat(excerpt).endsWith("[…truncated]");
    }

    @Test
    @DisplayName("commits — GitHub API newest-first 응답을 oldest-first로 역순 저장")
    void fetch_commitsSortedOldestFirst() {
        var commitNew = makeCommit("sha-new", "new commit", "2026-01-02T00:00:00Z");
        var commitOld = makeCommit("sha-old", "old commit", "2026-01-01T00:00:00Z");
        given(apiClient.listCommits(TOKEN, OWNER, REPO, LOGIN, 20)).willReturn(List.of(commitNew, commitOld));
        given(apiClient.getCommitDetail(TOKEN, OWNER, REPO, "sha-new")).willReturn(makeDetail("sha-new", "new commit", 10, 2));
        given(apiClient.getCommitDetail(TOKEN, OWNER, REPO, "sha-old")).willReturn(makeDetail("sha-old", "old commit", 5, 1));

        RepoMetadata metadata = fetcher.fetch(TOKEN, OWNER, REPO, LOGIN);

        assertThat(metadata.commits()).hasSize(2);
        assertThat(metadata.commits().get(0).sha()).isEqualTo("sha-old");
        assertThat(metadata.commits().get(1).sha()).isEqualTo("sha-new");
    }

    @Test
    @DisplayName("commits — subject는 message 첫 줄, bodyExcerpt는 두 번째 줄 이후 500자 cap")
    void fetch_commitSubjectAndBody() {
        String message = "feat: OAuth\n\nThis is the body of the commit.";
        var commit = makeCommit("sha1", message, "2026-01-01T00:00:00Z");
        given(apiClient.listCommits(TOKEN, OWNER, REPO, LOGIN, 20)).willReturn(List.of(commit));
        given(apiClient.getCommitDetail(TOKEN, OWNER, REPO, "sha1"))
                .willReturn(makeDetail("sha1", message, 5, 1));

        RepoMetadata metadata = fetcher.fetch(TOKEN, OWNER, REPO, LOGIN);

        assertThat(metadata.commits().get(0).subject()).isEqualTo("feat: OAuth");
        assertThat(metadata.commits().get(0).bodyExcerpt()).contains("This is the body");
    }

    @Test
    @DisplayName("pullRequests — 사용자 본인 PR만 포함, 시간 오름차순")
    void fetch_pullRequests_filteredByAuthorAndSorted() {
        var myPr = makePr(1, "My PR", LOGIN, "2026-01-01T00:00:00Z");
        var otherPr = makePr(2, "Other PR", "other-user", "2026-01-02T00:00:00Z");
        given(apiClient.listPullRequests(TOKEN, OWNER, REPO)).willReturn(List.of(myPr, otherPr));

        RepoMetadata metadata = fetcher.fetch(TOKEN, OWNER, REPO, LOGIN);

        assertThat(metadata.pullRequests()).hasSize(1);
        assertThat(metadata.pullRequests().get(0).number()).isEqualTo(1);
        assertThat(metadata.pullRequests().get(0).title()).isEqualTo("My PR");
    }

    @Test
    @DisplayName("issues — pull_request 필드 있는 항목은 제외")
    void fetch_issues_excludesPullRequests() {
        var issue = makeIssue(1, "Real issue", LOGIN, "2026-01-01T00:00:00Z", false);
        var prAsIssue = makeIssue(2, "PR disguised as issue", LOGIN, "2026-01-02T00:00:00Z", true);
        given(apiClient.listIssues(TOKEN, OWNER, REPO)).willReturn(List.of(issue, prAsIssue));

        RepoMetadata metadata = fetcher.fetch(TOKEN, OWNER, REPO, LOGIN);

        assertThat(metadata.issues()).hasSize(1);
        assertThat(metadata.issues().get(0).number()).isEqualTo(1);
    }

    @Test
    @DisplayName("languageBytes — API 맵을 그대로 반영")
    void fetch_languageBytes() {
        given(apiClient.getLanguages(TOKEN, OWNER, REPO)).willReturn(Map.of("Java", 5000L, "Kotlin", 2000L));

        RepoMetadata metadata = fetcher.fetch(TOKEN, OWNER, REPO, LOGIN);

        assertThat(metadata.languageBytes()).containsEntry("Java", 5000L);
    }

    // ── helpers ──

    private static GithubCommitDto makeCommit(String sha, String message, String date) {
        return new GithubCommitDto(sha,
                new GithubCommitDto.CommitInfo(message,
                        new GithubCommitDto.Committer(date)));
    }

    private static GithubCommitDetailDto makeDetail(String sha, String message, int adds, int dels) {
        return new GithubCommitDetailDto(sha,
                new GithubCommitDetailDto.CommitInfo(message),
                new GithubCommitDetailDto.Stats(adds, dels),
                List.of(new GithubCommitDetailDto.FileChange("src/Foo.java", "+public class Foo {}")));
    }

    private static GithubPrDto makePr(int number, String title, String login, String createdAt) {
        return new GithubPrDto(number, title, "body", "closed", 10, 2, createdAt,
                new GithubPrDto.User(login));
    }

    private static GithubIssueDto makeIssue(int number, String title, String login, String createdAt, boolean isPr) {
        return new GithubIssueDto(number, title, "body", "open", createdAt,
                new GithubIssueDto.User(login), isPr ? Map.of("url", "xxx") : null);
    }
}
