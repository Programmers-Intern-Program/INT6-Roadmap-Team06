package com.back.coach.service.github;

import com.back.coach.domain.github.entity.GithubAnalysis;
import com.back.coach.domain.github.entity.GithubConnection;
import com.back.coach.domain.github.repository.GithubAnalysisRepository;
import com.back.coach.domain.github.repository.GithubConnectionRepository;
import com.back.coach.domain.github.repository.GithubProjectRepository;
import com.back.coach.domain.user.entity.User;
import com.back.coach.domain.user.repository.UserRepository;
import com.back.coach.global.code.AuthProvider;
import com.back.coach.global.code.GithubAccessType;
import com.back.coach.support.IntegrationTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@DirtiesContext
class GithubAnalysisFlowIntegrationTest {

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
    static void overrideAiGatewayUrl(DynamicPropertyRegistry registry) {
        registry.add("ai.gateway.base-url", () -> "http://127.0.0.1:" + wireMock.port());
    }

    @Autowired UserRepository userRepository;
    @Autowired GithubConnectionRepository connectionRepository;
    @Autowired GithubProjectRepository projectRepository;
    @Autowired GithubAnalysisRepository analysisRepository;
    @Autowired GithubAnalysisService analysisService;
    @Autowired AnalysisPayloadJson payloadJson;

    @PersistenceContext EntityManager em;

    @Test
    @DisplayName("3-stage 흐름이 WireMock 위에서 끝까지 통과하고 github_analyses에 영속화된다")
    @Transactional
    void fullFlow_persistsAnalysisRow() {
        wireMock.resetAll();
        // Triage: 프롬프트에 "Candidates" 포함 → champion 1개 반환
        wireMock.stubFor(post("/v1/completions")
                .withRequestBody(matchingJsonPath("$.prompt", new com.github.tomakehurst.wiremock.matching.RegexPattern("(?s).*Candidates.*")))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"text\":\"{\\\"champions\\\":[{\\\"kind\\\":\\\"COMMIT\\\",\\\"ref\\\":\\\"abc\\\",\\\"reason\\\":\\\"OAuth\\\"}]}\"}")));
        // Per-repo summary: 프롬프트에 "Repository" + "repoId" 포함
        wireMock.stubFor(post("/v1/completions")
                .withRequestBody(matchingJsonPath("$.prompt", new com.github.tomakehurst.wiremock.matching.RegexPattern("(?s).*repoId:.*")))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"text\":\"{\\\"repoId\\\":\\\"PROJECT_ID\\\",\\\"repoName\\\":\\\"user/cool\\\",\\\"summary\\\":\\\"Spring Boot\\\",\\\"highlights\\\":[\\\"OAuth\\\"]}\"}")));
        // Synthesis: 프롬프트에 "Static Signals" 포함
        wireMock.stubFor(post("/v1/completions")
                .withRequestBody(matchingJsonPath("$.prompt", new com.github.tomakehurst.wiremock.matching.RegexPattern("(?s).*Static Signals.*")))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"text\":\"{\\\"techTags\\\":[{\\\"skillName\\\":\\\"Spring Boot\\\",\\\"tagReason\\\":\\\"백엔드\\\"}],\\\"depthEstimates\\\":[{\\\"skillName\\\":\\\"Spring Boot\\\",\\\"level\\\":\\\"PRACTICAL\\\",\\\"reason\\\":\\\"r\\\"}],\\\"evidences\\\":[{\\\"repoName\\\":\\\"user/cool\\\",\\\"type\\\":\\\"COMMIT\\\",\\\"source\\\":\\\"abc\\\",\\\"summary\\\":\\\"x\\\"}],\\\"finalTechProfile\\\":{\\\"confirmedSkills\\\":[\\\"Spring Boot\\\"],\\\"focusAreas\\\":[]}}\"}")));

        User user = userRepository.save(User.signupFromOAuth(AuthProvider.GITHUB, "gh-int-1", "int1@example.com"));
        GithubConnection connection = connectionRepository.save(GithubConnection.connect(
                user.getId(), "ghu-100", "intuser", GithubAccessType.OAUTH, "ghp_test_token"));
        com.back.coach.domain.github.entity.GithubProject project = newProject(user.getId(), connection.getId(),
                "user/cool", "Java",
                "{\"languageBytes\":{\"Java\":1000},\"commits\":[{\"sha\":\"abc\",\"subject\":\"feat: OAuth\",\"bodyExcerpt\":\"\",\"paths\":[\"X.java\"],\"additions\":10,\"deletions\":0,\"diffExcerpt\":\"diff body\"}],\"pullRequests\":[],\"issues\":[]}");
        projectRepository.save(project);
        em.flush();

        GithubAnalysisService.GithubAnalysisResult result = analysisService.run(
                user.getId(), connection.getId(),
                List.of(project.getId()), List.of(project.getId())
        );

        assertThat(result.id()).isNotNull();
        assertThat(result.version()).isEqualTo(1);
        assertThat(result.payload().finalTechProfile().confirmedSkills()).contains("Spring Boot");

        GithubAnalysis persisted = analysisRepository.findByIdAndUserId(result.id(), user.getId()).orElseThrow();
        AnalysisPayload restored = payloadJson.fromJson(persisted.getAnalysisPayload());
        assertThat(restored.depthEstimates()).extracting(p -> p.level().name()).contains("PRACTICAL");
        assertThat(restored.meta().triageFallback()).isFalse();
    }

    private static com.back.coach.domain.github.entity.GithubProject newProject(
            Long userId, Long connectionId, String fullName, String lang, String metadataJson) {
        try {
            var ctor = com.back.coach.domain.github.entity.GithubProject.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            var p = ctor.newInstance();
            org.springframework.test.util.ReflectionTestUtils.setField(p, "userId", userId);
            org.springframework.test.util.ReflectionTestUtils.setField(p, "githubConnectionId", connectionId);
            org.springframework.test.util.ReflectionTestUtils.setField(p, "repoFullName", fullName);
            org.springframework.test.util.ReflectionTestUtils.setField(p, "repoUrl", "https://github.com/" + fullName);
            org.springframework.test.util.ReflectionTestUtils.setField(p, "primaryLanguage", lang);
            org.springframework.test.util.ReflectionTestUtils.setField(p, "selected", true);
            org.springframework.test.util.ReflectionTestUtils.setField(p, "coreRepo", true);
            org.springframework.test.util.ReflectionTestUtils.setField(p, "metadataPayload", metadataJson);
            return p;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
