package com.back.coach.external.github;

import com.back.coach.external.github.dto.GithubCommitDetailDto;
import com.back.coach.external.github.dto.GithubCommitDto;
import com.back.coach.external.github.dto.GithubPrDto;
import com.back.coach.external.github.dto.GithubRepoDto;
import com.back.coach.external.github.dto.GithubUserInfoDto;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestGithubApiClientTest {

    private WireMockServer wireMock;
    private RestGithubApiClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        String base = "http://127.0.0.1:" + wireMock.port();
        client = new RestGithubApiClient(
                new GithubApiProperties(base, base, "client-id", "client-secret",
                        "connection-client-id", "connection-client-secret")
        );
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    // ── exchangeCode ──

    @Test
    @DisplayName("exchangeCode — form-encoded 응답에서 access_token 파싱")
    void exchangeCode_parsesFormEncodedToken() {
        wireMock.stubFor(post("/login/oauth/access_token")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/x-www-form-urlencoded")
                        .withBody("access_token=ghp_test123&token_type=bearer&scope=repo")));

        String token = client.exchangeCode("code-abc");

        assertThat(token).isEqualTo("ghp_test123");
        wireMock.verify(postRequestedFor(urlEqualTo("/login/oauth/access_token"))
                .withRequestBody(containing("client_id=connection-client-id"))
                .withRequestBody(containing("client_secret=connection-client-secret"))
                .withRequestBody(containing("code=code-abc")));
    }

    @Test
    @DisplayName("exchangeCode — connection 전용 설정이 없으면 기본 OAuth 설정으로 fallback")
    void exchangeCode_fallsBackToDefaultOAuthCredentials() {
        RestGithubApiClient fallbackClient = new RestGithubApiClient(
                new GithubApiProperties(
                        "http://127.0.0.1:" + wireMock.port(),
                        "http://127.0.0.1:" + wireMock.port(),
                        "client-id",
                        "client-secret",
                        "",
                        null
                )
        );
        wireMock.stubFor(post("/login/oauth/access_token")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/x-www-form-urlencoded")
                        .withBody("access_token=ghp_fallback&token_type=bearer&scope=repo")));

        String token = fallbackClient.exchangeCode("fallback-code");

        assertThat(token).isEqualTo("ghp_fallback");
        wireMock.verify(postRequestedFor(urlEqualTo("/login/oauth/access_token"))
                .withRequestBody(containing("client_id=client-id"))
                .withRequestBody(containing("client_secret=client-secret"))
                .withRequestBody(containing("code=fallback-code")));
    }

    @Test
    @DisplayName("exchangeCode — GitHub 오류 응답은 GITHUB_API_ERROR")
    void exchangeCode_errorResponse_throwsGithubApiError() {
        wireMock.stubFor(post("/login/oauth/access_token")
                .willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> client.exchangeCode("bad-code"))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.GITHUB_API_ERROR);
    }

    // ── getUserInfo ──

    @Test
    @DisplayName("getUserInfo — id(정수), login 파싱")
    void getUserInfo_parsesIdAndLogin() {
        wireMock.stubFor(get("/user")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":42,\"login\":\"testuser\",\"extra\":\"ignored\"}")));

        GithubUserInfoDto info = client.getUserInfo("ghp_token");

        assertThat(info.id()).isEqualTo(42L);
        assertThat(info.login()).isEqualTo("testuser");
    }

    @Test
    @DisplayName("getUserInfo — 429 응답은 GITHUB_RATE_LIMITED")
    void getUserInfo_rateLimited_throws() {
        wireMock.stubFor(get("/user")
                .willReturn(aResponse().withStatus(429)));

        assertThatThrownBy(() -> client.getUserInfo("token"))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.GITHUB_RATE_LIMITED);
    }

    @Test
    @DisplayName("getUserInfo — 5xx 응답은 GITHUB_API_ERROR")
    void getUserInfo_serverError_throws() {
        wireMock.stubFor(get("/user")
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.getUserInfo("token"))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.GITHUB_API_ERROR);
    }

    // ── listUserRepos ──

    @Test
    @DisplayName("listUserRepos — 저장소 목록 파싱 + affiliation에 organization_member 포함")
    void listUserRepos_parsesRepoList() {
        wireMock.stubFor(get(urlPathEqualTo("/user/repos"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {"node_id":"N1","full_name":"user/repo-a","html_url":"https://github.com/user/repo-a","language":"Java","default_branch":"main","fork":false},
                                  {"node_id":"N2","full_name":"user/repo-b","html_url":"https://github.com/user/repo-b","language":"Python","default_branch":"main","fork":false}
                                ]
                                """)));

        List<GithubRepoDto> repos = client.listUserRepos("ghp_token");

        assertThat(repos).hasSize(2);
        assertThat(repos.get(0).fullName()).isEqualTo("user/repo-a");
        assertThat(repos.get(1).language()).isEqualTo("Python");
        wireMock.verify(getRequestedFor(urlPathEqualTo("/user/repos"))
                .withQueryParam("affiliation", equalTo("owner,collaborator,organization_member"))
                .withQueryParam("per_page", equalTo("100")));
    }

    @Test
    @DisplayName("listUserRepos — Link 헤더 rel=\"next\" 따라 모든 페이지 fetch")
    void listUserRepos_followsLinkHeaderPagination() {
        String base = "http://127.0.0.1:" + wireMock.port();
        // page 1: 2 repos + Link to page 2
        wireMock.stubFor(get(urlPathEqualTo("/user/repos"))
                .withQueryParam("affiliation", equalTo("owner,collaborator,organization_member"))
                .withQueryParam("per_page", equalTo("100"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Link",
                                "<" + base + "/user/repos?page=2&per_page=100>; rel=\"next\", "
                                + "<" + base + "/user/repos?page=2&per_page=100>; rel=\"last\"")
                        .withBody("""
                                [
                                  {"node_id":"N1","full_name":"user/repo-a","html_url":"https://github.com/user/repo-a","language":"Java","default_branch":"main","fork":false},
                                  {"node_id":"N2","full_name":"user/repo-b","html_url":"https://github.com/user/repo-b","language":"Python","default_branch":"main","fork":false}
                                ]
                                """)));
        // page 2: 1 repo + no Link rel="next" (마지막 페이지)
        wireMock.stubFor(get(urlPathEqualTo("/user/repos"))
                .withQueryParam("page", equalTo("2"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {"node_id":"N3","full_name":"org/repo-c","html_url":"https://github.com/org/repo-c","language":"Go","default_branch":"main","fork":false}
                                ]
                                """)));

        List<GithubRepoDto> repos = client.listUserRepos("ghp_token");

        assertThat(repos).hasSize(3);
        assertThat(repos).extracting(GithubRepoDto::fullName)
                .containsExactly("user/repo-a", "user/repo-b", "org/repo-c");
    }

    @Test
    @DisplayName("listUserRepos — Link 헤더 없으면 1 페이지로 종료")
    void listUserRepos_noLinkHeader_stopsAtSinglePage() {
        wireMock.stubFor(get(urlPathEqualTo("/user/repos"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {"node_id":"N1","full_name":"user/repo-a","html_url":"https://github.com/user/repo-a","language":"Java","default_branch":"main","fork":false}
                                ]
                                """)));

        List<GithubRepoDto> repos = client.listUserRepos("ghp_token");

        assertThat(repos).hasSize(1);
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/user/repos")));
    }

    // ── getReadme ──

    @Test
    @DisplayName("getReadme — base64 인코딩 콘텐츠 디코딩")
    void getReadme_decodesBase64() {
        String encoded = Base64.getEncoder().encodeToString("# Hello World".getBytes());
        wireMock.stubFor(get("/repos/user/repo/readme")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"content\":\"" + encoded + "\",\"encoding\":\"base64\"}")));

        Optional<String> readme = client.getReadme("token", "user", "repo");

        assertThat(readme).contains("# Hello World");
    }

    @Test
    @DisplayName("getReadme — 404 응답은 Optional.empty()")
    void getReadme_notFound_returnsEmpty() {
        wireMock.stubFor(get("/repos/user/no-readme/readme")
                .willReturn(aResponse().withStatus(404)));

        Optional<String> readme = client.getReadme("token", "user", "no-readme");

        assertThat(readme).isEmpty();
    }

    // ── getLanguages ──

    @Test
    @DisplayName("getLanguages — 언어별 바이트 맵 파싱")
    void getLanguages_parsesMap() {
        wireMock.stubFor(get("/repos/user/repo/languages")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"Java\":5000,\"Kotlin\":3000}")));

        Map<String, Long> langs = client.getLanguages("token", "user", "repo");

        assertThat(langs).containsEntry("Java", 5000L).containsEntry("Kotlin", 3000L);
    }

    // ── getFileContent ──

    @Test
    @DisplayName("getFileContent — 존재하는 파일은 디코딩된 내용 반환")
    void getFileContent_returnsDecodedContent() {
        String encoded = Base64.getEncoder().encodeToString("<project/>".getBytes());
        wireMock.stubFor(get("/repos/user/repo/contents/pom.xml")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"content\":\"" + encoded + "\",\"encoding\":\"base64\"}")));

        Optional<String> content = client.getFileContent("token", "user", "repo", "pom.xml");

        assertThat(content).contains("<project/>");
    }

    @Test
    @DisplayName("getFileContent — 404 응답은 Optional.empty()")
    void getFileContent_notFound_returnsEmpty() {
        wireMock.stubFor(get(urlPathMatching("/repos/.*/contents/.*"))
                .willReturn(aResponse().withStatus(404)));

        Optional<String> content = client.getFileContent("token", "user", "repo", "missing.txt");

        assertThat(content).isEmpty();
    }

    // ── listCommits ──

    @Test
    @DisplayName("listCommits — 커밋 목록 파싱")
    void listCommits_parsesCommitList() {
        wireMock.stubFor(get(urlPathEqualTo("/repos/user/repo/commits"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {"sha":"abc123","commit":{"message":"feat: OAuth\\n\\nbody text","committer":{"date":"2026-01-01T00:00:00Z"}}},
                                  {"sha":"def456","commit":{"message":"fix: bug","committer":{"date":"2026-01-02T00:00:00Z"}}}
                                ]
                                """)));

        List<GithubCommitDto> commits = client.listCommits("token", "user", "repo", "testuser", 20);

        assertThat(commits).hasSize(2);
        assertThat(commits.get(0).sha()).isEqualTo("abc123");
        assertThat(commits.get(0).commit().message()).startsWith("feat: OAuth");
    }

    // ── getCommitDetail ──

    @Test
    @DisplayName("getCommitDetail — sha, stats, files 파싱")
    void getCommitDetail_parsesDetail() {
        wireMock.stubFor(get("/repos/user/repo/commits/abc123")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "sha": "abc123",
                                  "commit": {"message": "feat: OAuth"},
                                  "stats": {"additions": 50, "deletions": 5},
                                  "files": [{"filename": "src/Auth.java", "patch": "+import foo;"}]
                                }
                                """)));

        GithubCommitDetailDto detail = client.getCommitDetail("token", "user", "repo", "abc123");

        assertThat(detail.stats().additions()).isEqualTo(50);
        assertThat(detail.stats().deletions()).isEqualTo(5);
        assertThat(detail.files()).hasSize(1);
        assertThat(detail.files().get(0).filename()).isEqualTo("src/Auth.java");
    }

    // ── listPullRequests ──

    @Test
    @DisplayName("listPullRequests — PR 목록 파싱, pull_request 필드 무시")
    void listPullRequests_parsesPrList() {
        wireMock.stubFor(get(urlPathEqualTo("/repos/user/repo/pulls"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {"number":1,"title":"Add OAuth","body":"body","state":"closed","additions":100,"deletions":10,"created_at":"2026-01-01T00:00:00Z","user":{"login":"testuser"}}
                                ]
                                """)));

        List<GithubPrDto> prs = client.listPullRequests("token", "user", "repo");

        assertThat(prs).hasSize(1);
        assertThat(prs.get(0).title()).isEqualTo("Add OAuth");
        assertThat(prs.get(0).user().login()).isEqualTo("testuser");
    }
}
