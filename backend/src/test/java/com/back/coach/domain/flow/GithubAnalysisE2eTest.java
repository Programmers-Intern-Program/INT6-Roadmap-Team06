package com.back.coach.domain.flow;

import com.back.coach.domain.user.entity.User;
import com.back.coach.domain.user.repository.UserRepository;
import com.back.coach.external.github.GithubApiClient;
import com.back.coach.external.github.dto.GithubRepoDto;
import com.back.coach.external.github.dto.GithubUserInfoDto;
import com.back.coach.global.code.AuthProvider;
import com.back.coach.global.security.JwtTokenProvider;
import com.back.coach.support.ApiTestBase;
import com.back.coach.support.JwtAuthHelper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GithubAnalysisE2eTest extends ApiTestBase {

    private static final WireMockServer wireMock;

    static {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @DynamicPropertySource
    static void overrideAiGatewayUrl(DynamicPropertyRegistry registry) {
        registry.add("ai.gateway.base-url", () -> "http://127.0.0.1:" + wireMock.port());
    }

    @MockitoBean
    private GithubApiClient githubApiClient;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User user;
    private String authHeader;

    @BeforeEach
    void setUpUser() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        user = userRepository.save(User.signupFromOAuth(AuthProvider.GITHUB, "gh-" + unique, "e2e-" + unique + "@test.com"));
        authHeader = JwtAuthHelper.bearerToken(jwtTokenProvider, user.getId());
    }

    @Test
    @DisplayName("GitHub 연결 → 저장소 목록 조회 → 분석 실행 → 분석 결과 조회 전체 플로우")
    void fullFlow_connectAndAnalyse() throws Exception {
        // GitHub API mock
        given(githubApiClient.exchangeCode("auth-code-123")).willReturn("ghp_fake_token");
        given(githubApiClient.getUserInfo("ghp_fake_token"))
                .willReturn(new GithubUserInfoDto(99L, "testuser"));
        given(githubApiClient.listUserRepos("ghp_fake_token")).willReturn(List.of(
                new GithubRepoDto("node-1", "testuser/backend", "https://github.com/testuser/backend", "Java", "main", false, new GithubRepoDto.OwnerDto("testuser"))
        ));
        given(githubApiClient.getReadme(anyString(), anyString(), anyString())).willReturn(Optional.empty());
        given(githubApiClient.getLanguages(anyString(), anyString(), anyString())).willReturn(Map.of("Java", 10000L));
        given(githubApiClient.listCommits(anyString(), anyString(), anyString(), anyString(), any(int.class))).willReturn(List.of());
        given(githubApiClient.listPullRequests(anyString(), anyString(), anyString())).willReturn(List.of());
        given(githubApiClient.listIssues(anyString(), anyString(), anyString())).willReturn(List.of());

        // LLM mock — Slice 4b highlight 형식
        stubLlm("Candidates", triageResponse());
        stubLlm("repoId:", summaryResponse());
        stubLlm("Static Signals", synthesisResponse());

        // 1. GitHub 연결
        String connectResult = mockMvc.perform(
                        post("/api/github/connections")
                                .header("Authorization", authHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"authorizationCode\":\"auth-code-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.githubConnectionId").isNotEmpty())
                .andExpect(jsonPath("$.data.githubLogin").value("testuser"))
                .andReturn().getResponse().getContentAsString();

        long connectionId = extractLong(connectResult, "githubConnectionId");

        // 2. 저장소 목록 조회
        String reposResult = mockMvc.perform(
                        get("/api/github/repositories")
                                .header("Authorization", authHeader)
                                .param("githubConnectionId", String.valueOf(connectionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.repositories").isArray())
                .andExpect(jsonPath("$.data.repositories[0].repoFullName").value("testuser/backend"))
                .andReturn().getResponse().getContentAsString();

        long repoId = extractLong(reposResult, "repositoryId");

        // 3. 분석 실행
        String analysisResult = mockMvc.perform(
                        post("/api/github-analyses")
                                .header("Authorization", authHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(String.format(
                                        "{\"githubConnectionId\":%d,\"selectedRepositoryIds\":[%d],\"coreRepositoryIds\":[%d]}",
                                        connectionId, repoId, repoId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.githubAnalysisId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        long analysisId = extractLong(analysisResult, "githubAnalysisId");

        // 4. 분석 결과 조회 — Slice 4b highlight 형식 검증
        mockMvc.perform(
                        get("/api/github-analyses/{id}", analysisId)
                                .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.repoSummaries[0].highlights[0].text").value("Spring Boot 도입"))
                .andExpect(jsonPath("$.data.repoSummaries[0].highlights[0].status").value("ADOPTED"))
                .andExpect(jsonPath("$.data.repoSummaries[0].highlights[1].text").value("GraphQL 시도 후 제거"))
                .andExpect(jsonPath("$.data.repoSummaries[0].highlights[1].status").value("REVERSED"))
                .andExpect(jsonPath("$.data.finalTechProfile.confirmedSkills[0]").value("Spring Boot"));
    }

    @Test
    @DisplayName("REVERSED highlight가 API 응답 JSON에 올바르게 직렬화된다")
    void analysis_reversedHighlightSerializesCorrectly() throws Exception {
        given(githubApiClient.exchangeCode("code-reversed")).willReturn("ghp_rev_token");
        given(githubApiClient.getUserInfo("ghp_rev_token"))
                .willReturn(new GithubUserInfoDto(88L, "revuser"));
        given(githubApiClient.listUserRepos("ghp_rev_token")).willReturn(List.of(
                new GithubRepoDto("node-2", "revuser/api", "https://github.com/revuser/api", "Java", "main", false, new GithubRepoDto.OwnerDto("revuser"))
        ));
        given(githubApiClient.getReadme(anyString(), anyString(), anyString())).willReturn(Optional.empty());
        given(githubApiClient.getLanguages(anyString(), anyString(), anyString())).willReturn(Map.of());
        given(githubApiClient.listCommits(anyString(), anyString(), anyString(), anyString(), any(int.class))).willReturn(List.of());
        given(githubApiClient.listPullRequests(anyString(), anyString(), anyString())).willReturn(List.of());
        given(githubApiClient.listIssues(anyString(), anyString(), anyString())).willReturn(List.of());

        stubLlm("Candidates", triageResponse());
        stubLlm("repoId:", summaryResponseWithReversed());
        stubLlm("Static Signals", synthesisResponse());

        String connectResult = mockMvc.perform(
                        post("/api/github/connections")
                                .header("Authorization", authHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"authorizationCode\":\"code-reversed\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long connectionId = extractLong(connectResult, "githubConnectionId");

        String reposResult = mockMvc.perform(
                        get("/api/github/repositories")
                                .header("Authorization", authHeader)
                                .param("githubConnectionId", String.valueOf(connectionId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long repoId = extractLong(reposResult, "repositoryId");

        String analysisResult = mockMvc.perform(
                        post("/api/github-analyses")
                                .header("Authorization", authHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(String.format(
                                        "{\"githubConnectionId\":%d,\"selectedRepositoryIds\":[%d],\"coreRepositoryIds\":[%d]}",
                                        connectionId, repoId, repoId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long analysisId = extractLong(analysisResult, "githubAnalysisId");

        mockMvc.perform(
                        get("/api/github-analyses/{id}", analysisId)
                                .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.repoSummaries[0].highlights[0].status").value("REVERSED"))
                .andExpect(jsonPath("$.data.repoSummaries[0].highlights[0].text").isNotEmpty());
    }

    private void stubLlm(String promptContains, String content) {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post("/v1/chat/completions")
                .withRequestBody(matchingJsonPath("$.messages[0].content",
                        new com.github.tomakehurst.wiremock.matching.RegexPattern("(?s).*" + promptContains + ".*")))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(chatResponse(content))));
    }

    private static String chatResponse(String content) {
        String escaped = content.replace("\"", "\\\"");
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"" + escaped + "\"}}]}";
    }

    private static String triageResponse() {
        return "{\"champions\":[{\"kind\":\"COMMIT\",\"ref\":\"abc123\",\"reason\":\"Spring Boot 핵심 로직\"}]}";
    }

    private static String summaryResponse() {
        return "{\"repoId\":\"REPO_ID_PLACEHOLDER\",\"repoName\":\"testuser/backend\",\"summary\":\"Spring Boot 백엔드 서비스\"," +
                "\"highlights\":[{\"text\":\"Spring Boot 도입\",\"status\":\"ADOPTED\"},{\"text\":\"GraphQL 시도 후 제거\",\"status\":\"REVERSED\"}]}";
    }

    private static String summaryResponseWithReversed() {
        return "{\"repoId\":\"REPO_ID_PLACEHOLDER\",\"repoName\":\"revuser/api\",\"summary\":\"실험적 아키텍처\"," +
                "\"highlights\":[{\"text\":\"마이크로서비스 전환 시도\",\"status\":\"REVERSED\"}]}";
    }

    private static String synthesisResponse() {
        return "{\"techTags\":[{\"skillName\":\"Spring Boot\",\"tagReason\":\"백엔드 프레임워크\"}]," +
                "\"depthEstimates\":[{\"skillName\":\"Spring Boot\",\"level\":\"PRACTICAL\",\"reason\":\"일관된 사용\"}]," +
                "\"evidences\":[{\"repoName\":\"testuser/backend\",\"type\":\"COMMIT\",\"source\":\"abc123\",\"summary\":\"핵심 로직\"}]," +
                "\"finalTechProfile\":{\"confirmedSkills\":[\"Spring Boot\"],\"focusAreas\":[\"백엔드\"]}}";
    }

    private static long extractLong(String json, String field) {
        int idx = json.indexOf("\"" + field + "\":");
        if (idx < 0) throw new AssertionError(field + " not found in: " + json);
        int start = idx + field.length() + 3;
        // handle quoted string IDs
        if (json.charAt(start) == '"') {
            int end = json.indexOf('"', start + 1);
            return Long.parseLong(json.substring(start + 1, end));
        }
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)))) end++;
        return Long.parseLong(json.substring(start, end));
    }
}
