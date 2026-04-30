package com.back.coach.domain.github.controller;

import com.back.coach.domain.github.entity.GithubConnection;
import com.back.coach.domain.github.entity.GithubProject;
import com.back.coach.domain.github.repository.GithubConnectionRepository;
import com.back.coach.domain.github.repository.GithubProjectRepository;
import com.back.coach.domain.user.entity.User;
import com.back.coach.domain.user.repository.UserRepository;
import com.back.coach.global.code.AuthProvider;
import com.back.coach.global.code.GithubAccessType;
import com.back.coach.global.security.JwtTokenProvider;
import com.back.coach.support.ApiTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DirtiesContext
class GithubConnectionControllerTest extends ApiTestBase {

    private static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @DynamicPropertySource
    static void overrideGithubApiUrls(DynamicPropertyRegistry registry) {
        String base = "http://127.0.0.1:" + wireMock.port();
        registry.add("github.api.base-url", () -> base);
        registry.add("github.api.oauth-base-url", () -> base);
        registry.add("github.api.client-id", () -> "test-client-id");
        registry.add("github.api.client-secret", () -> "test-client-secret");
    }

    @Autowired UserRepository userRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired ObjectMapper objectMapper;
    @Autowired GithubConnectionRepository connectionRepository;
    @Autowired GithubProjectRepository projectRepository;

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    // ── POST /api/github/connections ──

    @Test
    @DisplayName("POST /api/github/connections — 인증 없이 401")
    void post_withoutAuth_unauthorized() throws Exception {
        mockMvc.perform(post("/api/github/connections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authorizationCode\":\"code\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/github/connections — authorizationCode 누락 시 400")
    void post_missingCode_badRequest() throws Exception {
        User user = userRepository.save(User.signupFromOAuth(AuthProvider.GITHUB, "gh-conn-1", "conn1@example.com"));
        String token = jwtTokenProvider.createAccessToken(user.getId());

        mockMvc.perform(post("/api/github/connections")
                        .cookie(new Cookie("accessToken", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/github/connections — 정상 흐름: GithubConnection + GithubProject 생성, 200 반환")
    void post_happyPath_createsConnectionAndProjects() throws Exception {
        User user = userRepository.save(User.signupFromOAuth(AuthProvider.GITHUB, "gh-conn-2", "conn2@example.com"));
        String token = jwtTokenProvider.createAccessToken(user.getId());

        stubOAuthExchange("ghp_test_token");
        stubUserInfo(99L, "testuser");
        stubUserRepos(List.of(Map.of(
                "node_id", "N1",
                "full_name", "testuser/cool-repo",
                "html_url", "https://github.com/testuser/cool-repo",
                "language", "Java",
                "default_branch", "main",
                "fork", false
        )));
        stubReadme("testuser", "cool-repo", "# Cool Repo");
        stubLanguages("testuser", "cool-repo", Map.of("Java", 5000));
        stubNoDepFiles("testuser", "cool-repo");
        stubCommits("testuser", "cool-repo", "testuser", List.of());
        stubPullRequests("testuser", "cool-repo", List.of());
        stubIssues("testuser", "cool-repo", List.of());

        mockMvc.perform(post("/api/github/connections")
                        .cookie(new Cookie("accessToken", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authorizationCode\":\"test-code\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.githubConnectionId").isNotEmpty())
                .andExpect(jsonPath("$.data.githubLogin").value("testuser"));

        // DB assertions
        List<GithubConnection> connections = connectionRepository.findByUserIdOrderByConnectedAtDesc(user.getId());
        assertThat(connections).hasSize(1);
        assertThat(connections.get(0).getGithubLogin()).isEqualTo("testuser");
        assertThat(connections.get(0).getAccessToken()).isEqualTo("ghp_test_token");

        List<GithubProject> projects = projectRepository.findByGithubConnectionIdAndUserId(
                connections.get(0).getId(), user.getId());
        assertThat(projects).hasSize(1);
        assertThat(projects.get(0).getRepoFullName()).isEqualTo("testuser/cool-repo");
        assertThat(projects.get(0).getMetadataPayload()).contains("languageBytes");
    }

    @Test
    @DisplayName("POST /api/github/connections — GitHub 코드 교환 실패 시 에러 응답")
    void post_badCode_returnsError() throws Exception {
        User user = userRepository.save(User.signupFromOAuth(AuthProvider.GITHUB, "gh-conn-3", "conn3@example.com"));
        String token = jwtTokenProvider.createAccessToken(user.getId());

        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post("/login/oauth/access_token")
                .willReturn(aResponse().withStatus(401)));

        mockMvc.perform(post("/api/github/connections")
                        .cookie(new Cookie("accessToken", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authorizationCode\":\"bad-code\"}"))
                .andExpect(status().is5xxServerError());
    }

    // ── GET /api/github/repositories ──

    @Test
    @DisplayName("GET /api/github/repositories — 인증 없이 401")
    void get_withoutAuth_unauthorized() throws Exception {
        mockMvc.perform(get("/api/github/repositories").param("githubConnectionId", "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/github/repositories — 정상 조회: 저장소 목록 반환")
    void get_happyPath_returnsRepoList() throws Exception {
        User user = userRepository.save(User.signupFromOAuth(AuthProvider.GITHUB, "gh-conn-4", "conn4@example.com"));
        String token = jwtTokenProvider.createAccessToken(user.getId());

        GithubConnection conn = connectionRepository.save(
                GithubConnection.connect(user.getId(), "gh-uid-100", "testuser2", GithubAccessType.OAUTH, "ghp_tok"));
        GithubProject project = GithubProject.create(user.getId(), conn.getId(),
                "N10", "testuser2/my-repo", "https://github.com/testuser2/my-repo", "Java", "main");
        projectRepository.save(project);

        mockMvc.perform(get("/api/github/repositories")
                        .cookie(new Cookie("accessToken", token))
                        .param("githubConnectionId", String.valueOf(conn.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.githubConnectionId").value(String.valueOf(conn.getId())))
                .andExpect(jsonPath("$.data.repositories[0].repoFullName").value("testuser2/my-repo"));
    }

    @Test
    @DisplayName("GET /api/github/repositories — 타인 connectionId는 404")
    void get_foreignConnectionId_notFound() throws Exception {
        User user = userRepository.save(User.signupFromOAuth(AuthProvider.GITHUB, "gh-conn-5", "conn5@example.com"));
        String token = jwtTokenProvider.createAccessToken(user.getId());

        mockMvc.perform(get("/api/github/repositories")
                        .cookie(new Cookie("accessToken", token))
                        .param("githubConnectionId", "99999"))
                .andExpect(status().isNotFound());
    }

    // ── WireMock stub helpers ──

    private void stubOAuthExchange(String accessToken) {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post("/login/oauth/access_token")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/x-www-form-urlencoded")
                        .withBody("access_token=" + accessToken + "&token_type=bearer")));
    }

    private void stubUserInfo(long id, String login) throws Exception {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get("/user")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(Map.of("id", id, "login", login)))));
    }

    private void stubUserRepos(List<Map<String, Object>> repos) throws Exception {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/user/repos"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(repos))));
    }

    private void stubReadme(String owner, String repo, String content) {
        String encoded = Base64.getEncoder().encodeToString(content.getBytes());
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get("/repos/" + owner + "/" + repo + "/readme")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"content\":\"" + encoded + "\",\"encoding\":\"base64\"}")));
    }

    private void stubLanguages(String owner, String repo, Map<String, Integer> langs) throws Exception {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get("/repos/" + owner + "/" + repo + "/languages")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(langs))));
    }

    private void stubNoDepFiles(String owner, String repo) {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathMatching("/repos/" + owner + "/" + repo + "/contents/.*"))
                .willReturn(aResponse().withStatus(404)));
    }

    private void stubCommits(String owner, String repo, String author, List<?> commits) throws Exception {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/repos/" + owner + "/" + repo + "/commits"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(commits))));
    }

    private void stubPullRequests(String owner, String repo, List<?> prs) throws Exception {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/repos/" + owner + "/" + repo + "/pulls"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(prs))));
    }

    private void stubIssues(String owner, String repo, List<?> issues) throws Exception {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/repos/" + owner + "/" + repo + "/issues"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(issues))));
    }
}
