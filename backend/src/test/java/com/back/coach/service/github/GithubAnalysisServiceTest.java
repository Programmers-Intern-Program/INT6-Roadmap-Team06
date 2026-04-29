package com.back.coach.service.github;

import com.back.coach.domain.github.entity.GithubAnalysis;
import com.back.coach.domain.github.entity.GithubProject;
import com.back.coach.domain.github.repository.GithubAnalysisRepository;
import com.back.coach.domain.github.repository.GithubConnectionRepository;
import com.back.coach.domain.github.repository.GithubProjectRepository;
import com.back.coach.external.llm.LlmClient;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.back.coach.service.github.summary.RepoSummaryPromptBuilder;
import com.back.coach.service.github.summary.RepoSummaryResponseParser;
import com.back.coach.service.github.synthesis.SynthesisPromptBuilder;
import com.back.coach.service.github.synthesis.SynthesisResponseParser;
import com.back.coach.service.github.triage.ChampionTriagePromptBuilder;
import com.back.coach.service.github.triage.ChampionTriageResponseParser;
import com.back.coach.service.github.triage.ChampionTriageService;
import com.back.coach.service.github.summary.DiffPreprocessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class GithubAnalysisServiceTest {

    static final long USER_ID = 100L;
    static final long CONNECTION_ID = 200L;

    GithubConnectionRepository connectionRepo;
    GithubProjectRepository projectRepo;
    GithubAnalysisRepository analysisRepo;
    LlmClient llmClient;

    GithubAnalysisService service;

    String triageJson;
    String repoSummaryJson;
    String synthesisJson;

    @BeforeEach
    void setUp() {
        connectionRepo = mock(GithubConnectionRepository.class);
        projectRepo = mock(GithubProjectRepository.class);
        analysisRepo = mock(GithubAnalysisRepository.class);
        llmClient = mock(LlmClient.class);

        ChampionTriageService triageService = new ChampionTriageService(
                llmClient, new ChampionTriagePromptBuilder(), new ChampionTriageResponseParser());

        service = new GithubAnalysisService(
                connectionRepo, projectRepo, analysisRepo,
                new StaticSignalAggregator(),
                triageService,
                new DiffPreprocessor(),
                new RepoSummaryPromptBuilder(),
                new RepoSummaryResponseParser(),
                new SynthesisPromptBuilder(),
                new SynthesisResponseParser(),
                new AnalysisPayloadJson(),
                llmClient
        );

        triageJson = """
                {"champions":[{"kind":"COMMIT","ref":"abc","reason":"OAuth"}]}
                """;
        repoSummaryJson = """
                {"repoId":"1","repoName":"user/a","summary":"Spring Boot","highlights":["OAuth"]}
                """;
        synthesisJson = """
                {
                  "techTags":[{"skillName":"Spring Boot","tagReason":"백엔드"}],
                  "depthEstimates":[{"skillName":"Spring Boot","level":"PRACTICAL","reason":"r"}],
                  "evidences":[{"repoName":"user/a","type":"COMMIT","source":"abc","summary":"x"}],
                  "finalTechProfile":{"confirmedSkills":["Spring Boot"],"focusAreas":[]}
                }
                """;
    }

    @Test
    @DisplayName("연결 소유권이 없으면 FORBIDDEN")
    void run_connectionNotOwned_throwsForbidden() {
        given(connectionRepo.existsByIdAndUserId(CONNECTION_ID, USER_ID)).willReturn(false);

        assertThatThrownBy(() -> service.run(USER_ID, CONNECTION_ID, List.of(1L), List.of(1L)))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("첫 분석은 version=1로 저장된다")
    void run_firstAnalysis_versionIsOne() {
        primeRepos();
        given(analysisRepo.findMaxVersionByUserId(USER_ID)).willReturn(null);
        given(analysisRepo.save(any(GithubAnalysis.class))).willAnswer(inv -> withId(inv.getArgument(0), 7L));
        primeLlm();

        GithubAnalysisService.GithubAnalysisResult result =
                service.run(USER_ID, CONNECTION_ID, List.of(1L), List.of(1L));

        assertThat(result.version()).isEqualTo(1);
        assertThat(result.id()).isEqualTo(7L);
        assertThat(result.payload().finalTechProfile().confirmedSkills()).contains("Spring Boot");
        assertThat(result.payload().meta().triageFallback()).isFalse();
    }

    @Test
    @DisplayName("두 번째 분석은 version=N+1로 저장된다")
    void run_secondAnalysis_versionIsNplus1() {
        primeRepos();
        given(analysisRepo.findMaxVersionByUserId(USER_ID)).willReturn(3);
        given(analysisRepo.save(any(GithubAnalysis.class))).willAnswer(inv -> withId(inv.getArgument(0), 8L));
        primeLlm();

        GithubAnalysisService.GithubAnalysisResult result =
                service.run(USER_ID, CONNECTION_ID, List.of(1L), List.of(1L));

        assertThat(result.version()).isEqualTo(4);
    }

    @Test
    @DisplayName("Triage가 fallback되면 meta.triageFallback=true로 기록된다")
    void run_triageFallback_setsFlag() {
        primeRepos();
        given(analysisRepo.findMaxVersionByUserId(USER_ID)).willReturn(null);
        given(analysisRepo.save(any(GithubAnalysis.class))).willAnswer(inv -> withId(inv.getArgument(0), 9L));
        // triage 호출(첫 호출) → timeout, summary 호출(두 번째) → 정상, synthesis 호출(세 번째) → 정상
        given(llmClient.complete(anyString()))
                .willThrow(new ServiceException(ErrorCode.LLM_TIMEOUT))
                .willReturn(repoSummaryJson)
                .willReturn(synthesisJson);

        GithubAnalysisService.GithubAnalysisResult result =
                service.run(USER_ID, CONNECTION_ID, List.of(1L), List.of(1L));

        assertThat(result.payload().meta().triageFallback()).isTrue();
    }

    @Test
    @DisplayName("Stage 2 LLM 에러는 그대로 surface된다")
    void run_stage2LlmError_propagates() {
        primeRepos();
        given(analysisRepo.findMaxVersionByUserId(USER_ID)).willReturn(null);
        given(llmClient.complete(anyString()))
                .willReturn(triageJson)
                .willThrow(new ServiceException(ErrorCode.LLM_TIMEOUT));

        assertThatThrownBy(() -> service.run(USER_ID, CONNECTION_ID, List.of(1L), List.of(1L)))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LLM_TIMEOUT);
    }

    @Test
    @DisplayName("coreRepoIds가 selectedRepoIds의 부분집합이 아니면 INVALID_INPUT")
    void run_coreNotSubsetOfSelected_throws() {
        given(connectionRepo.existsByIdAndUserId(CONNECTION_ID, USER_ID)).willReturn(true);

        assertThatThrownBy(() -> service.run(USER_ID, CONNECTION_ID, List.of(1L), List.of(2L)))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    private void primeRepos() {
        given(connectionRepo.existsByIdAndUserId(CONNECTION_ID, USER_ID)).willReturn(true);
        GithubProject project = makeProject(1L, "user/a", "Java",
                "{\"languageBytes\":{\"Java\":1000},\"commits\":[{\"sha\":\"abc\",\"subject\":\"feat: OAuth\",\"bodyExcerpt\":\"\",\"paths\":[\"X.java\"],\"additions\":10,\"deletions\":0,\"diffExcerpt\":\"diff body\"}],\"pullRequests\":[],\"issues\":[]}");
        given(projectRepo.findByUserIdAndGithubConnectionId(USER_ID, CONNECTION_ID))
                .willReturn(List.of(project));
    }

    private void primeLlm() {
        given(llmClient.complete(anyString()))
                .willReturn(triageJson)
                .willReturn(repoSummaryJson)
                .willReturn(synthesisJson);
    }

    private static GithubProject makeProject(Long id, String fullName, String lang, String metadataJson) {
        GithubProject p;
        try {
            var ctor = GithubProject.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            p = ctor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        ReflectionTestUtils.setField(p, "id", id);
        ReflectionTestUtils.setField(p, "userId", USER_ID);
        ReflectionTestUtils.setField(p, "githubConnectionId", CONNECTION_ID);
        ReflectionTestUtils.setField(p, "repoFullName", fullName);
        ReflectionTestUtils.setField(p, "repoUrl", "https://github.com/" + fullName);
        ReflectionTestUtils.setField(p, "primaryLanguage", lang);
        ReflectionTestUtils.setField(p, "selected", true);
        ReflectionTestUtils.setField(p, "coreRepo", true);
        ReflectionTestUtils.setField(p, "metadataPayload", metadataJson);
        return p;
    }

    private static GithubAnalysis withId(GithubAnalysis a, long id) {
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }
}
