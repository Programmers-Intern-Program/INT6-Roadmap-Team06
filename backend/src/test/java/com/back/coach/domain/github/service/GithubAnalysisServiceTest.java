package com.back.coach.domain.github.service;

import com.back.coach.domain.github.dto.GithubAnalysisPayload;
import com.back.coach.domain.github.entity.GithubAnalysis;
import com.back.coach.domain.github.entity.GithubConnection;
import com.back.coach.domain.github.entity.GithubProject;
import com.back.coach.domain.github.repository.GithubAnalysisRepository;
import com.back.coach.domain.github.repository.GithubConnectionRepository;
import com.back.coach.domain.github.repository.GithubProjectRepository;
import com.back.coach.domain.github.service.fetcher.GithubMetadataFetcher;
import com.back.coach.global.code.GithubAccessType;
import com.back.coach.external.llm.LlmClient;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.back.coach.domain.github.service.summary.RepoSummaryPromptBuilder;
import com.back.coach.domain.github.service.summary.RepoSummaryResponseParser;
import com.back.coach.domain.github.service.synthesis.SynthesisPromptBuilder;
import com.back.coach.domain.github.service.synthesis.SynthesisResponseParser;
import com.back.coach.domain.github.service.triage.ChampionTriagePromptBuilder;
import com.back.coach.domain.github.service.triage.ChampionTriageResponseParser;
import com.back.coach.domain.github.service.triage.ChampionTriageService;
import com.back.coach.domain.github.service.summary.DiffPreprocessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class GithubAnalysisServiceTest {

    static final long USER_ID = 100L;
    static final long CONNECTION_ID = 200L;

    GithubConnectionRepository connectionRepo;
    GithubProjectRepository projectRepo;
    GithubAnalysisRepository analysisRepo;
    LlmClient llmClient;
    GithubMetadataFetcher metadataFetcher;

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
        metadataFetcher = mock(GithubMetadataFetcher.class);

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
                new GithubAnalysisPayloadJson(),
                llmClient,
                transactionTemplate(),
                mock(com.back.coach.domain.context.service.ContextSnapshotPublisher.class),
                metadataFetcher
        );

        triageJson = """
                {"champions":[{"kind":"COMMIT","ref":"abc","reason":"OAuth"}]}
                """;
        repoSummaryJson = """
                {"repoId":"1","repoName":"user/a","summary":"Spring Boot","highlights":[{"text":"OAuth","status":"ADOPTED"}]}
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
        given(connectionRepo.findByIdAndUserId(CONNECTION_ID, USER_ID)).willReturn(Optional.empty());

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
        GithubAnalysisPayload.AnalysisTrace trace = result.payload().analysisTrace();
        assertThat(trace).isNotNull();
        assertThat(trace.repositories()).singleElement().satisfies(repo -> {
            assertThat(repo.repoId()).isEqualTo("1");
            assertThat(repo.repoName()).isEqualTo("user/a");
            assertThat(repo.core()).isTrue();
            assertThat(repo.commitCount()).isEqualTo(1);
            assertThat(repo.pullRequestCount()).isZero();
            assertThat(repo.issueCount()).isZero();
            assertThat(repo.languageCount()).isEqualTo(1);
            assertThat(repo.dependencyFileCount()).isZero();
        });
        assertThat(trace.triage()).singleElement().satisfies(stage -> {
            assertThat(stage.promptVersion()).isEqualTo(ChampionTriagePromptBuilder.VERSION);
            assertThat(stage.promptBytes()).isPositive();
            assertThat(stage.elapsedMs()).isNotNegative();
            assertThat(stage.fallback()).isFalse();
            assertThat(stage.champions()).singleElement().satisfies(champion -> {
                assertThat(champion.kind()).isEqualTo("COMMIT");
                assertThat(champion.ref()).isEqualTo("abc");
                assertThat(champion.reason()).isEqualTo("OAuth");
            });
        });
        assertThat(trace.repoSummaries()).singleElement().satisfies(stage -> {
            assertThat(stage.promptVersion()).isEqualTo(RepoSummaryPromptBuilder.VERSION);
            assertThat(stage.promptBytes()).isPositive();
            assertThat(stage.elapsedMs()).isNotNegative();
            assertThat(stage.highlightCount()).isEqualTo(1);
            assertThat(stage.summaryLength()).isEqualTo("Spring Boot".length());
        });
        assertThat(trace.synthesis()).satisfies(stage -> {
            assertThat(stage.promptVersion()).isEqualTo(SynthesisPromptBuilder.VERSION);
            assertThat(stage.promptBytes()).isPositive();
            assertThat(stage.elapsedMs()).isNotNegative();
            assertThat(stage.techTagCount()).isEqualTo(1);
            assertThat(stage.depthEstimateCount()).isEqualTo(1);
            assertThat(stage.evidenceCount()).isEqualTo(1);
            assertThat(stage.confirmedSkillCount()).isEqualTo(1);
        });
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
    @DisplayName("Triage LLM 실패해도 fallback commit으로 분석이 완료된다")
    void run_triageFallback_analysisCompletes() {
        primeRepos();
        given(analysisRepo.findMaxVersionByUserId(USER_ID)).willReturn(null);
        given(analysisRepo.save(any(GithubAnalysis.class))).willAnswer(inv -> withId(inv.getArgument(0), 9L));
        // triage 호출(첫 호출) → timeout, summary 호출(두 번째) → 정상, synthesis 호출(세 번째) → 정상
        given(llmClient.complete(anyString(), anyString()))
                .willThrow(new ServiceException(ErrorCode.LLM_TIMEOUT))
                .willReturn(repoSummaryJson)
                .willReturn(synthesisJson);

        GithubAnalysisService.GithubAnalysisResult result =
                service.run(USER_ID, CONNECTION_ID, List.of(1L), List.of(1L));

        assertThat(result.version()).isEqualTo(1);
        assertThat(result.payload().finalTechProfile()).isNotNull();
        assertThat(result.payload().analysisTrace().triage())
                .singleElement()
                .satisfies(stage -> assertThat(stage.fallback()).isTrue());
    }

    @Test
    @DisplayName("Stage 2 LLM 에러는 그대로 surface된다")
    void run_stage2LlmError_propagates() {
        primeRepos();
        given(analysisRepo.findMaxVersionByUserId(USER_ID)).willReturn(null);
        given(llmClient.complete(anyString(), anyString()))
                .willReturn(triageJson)
                .willThrow(new ServiceException(ErrorCode.LLM_TIMEOUT));

        assertThatThrownBy(() -> service.run(USER_ID, CONNECTION_ID, List.of(1L), List.of(1L)))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LLM_TIMEOUT);
    }

    @Test
    @DisplayName("coreRepoIds가 selectedRepoIds의 부분집합이 아니면 INVALID_INPUT")
    void run_coreNotSubsetOfSelected_throws() {
        assertThatThrownBy(() -> service.run(USER_ID, CONNECTION_ID, List.of(1L), List.of(2L)))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("core repo metadata fetch가 전부 실패하면 성공 분석으로 저장하지 않는다")
    void run_allCoreMetadataFetchFailed_throws() {
        primeConnectionAndProjects(makeProject(1L, "user/a", "Java", "{}"));
        given(metadataFetcher.fetch(anyString(), anyString(), anyString(), anyString()))
                .willThrow(new ServiceException(ErrorCode.GITHUB_API_ERROR));

        assertThatThrownBy(() -> service.run(USER_ID, CONNECTION_ID, List.of(1L), List.of(1L)))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GITHUB_API_ERROR);
        verify(analysisRepo, never()).save(any(GithubAnalysis.class));
    }

    @Test
    @DisplayName("core repo metadata가 전부 비어 있으면 성공 분석으로 저장하지 않는다")
    void run_allCoreMetadataEmpty_throwsAnalysisFailed() {
        primeConnectionAndProjects(makeProject(1L, "user/a", "Java", "{}"));
        given(metadataFetcher.fetch(anyString(), anyString(), anyString(), anyString()))
                .willReturn(emptyMetadata());

        assertThatThrownBy(() -> service.run(USER_ID, CONNECTION_ID, List.of(1L), List.of(1L)))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ANALYSIS_FAILED);
        verify(analysisRepo, never()).save(any(GithubAnalysis.class));
    }

    @Test
    @DisplayName("core repo 일부 fetch 실패 시 유효한 repo만으로 분석을 저장한다")
    void run_partialCoreMetadataFetchFailure_analyzesSuccessfulCoreRepos() {
        primeConnectionAndProjects(
                makeProject(1L, "user/a", "Java", "{}"),
                makeProject(2L, "user/b", "Java", "{}")
        );
        given(metadataFetcher.fetch(eq("token-abc"), eq("user"), eq("a"), eq("user")))
                .willThrow(new ServiceException(ErrorCode.GITHUB_API_ERROR));
        given(metadataFetcher.fetch(eq("token-abc"), eq("user"), eq("b"), eq("user")))
                .willReturn(sampleMetadata());
        given(analysisRepo.findMaxVersionByUserId(USER_ID)).willReturn(null);
        given(analysisRepo.save(any(GithubAnalysis.class))).willAnswer(inv -> withId(inv.getArgument(0), 10L));
        primeLlm();

        GithubAnalysisService.GithubAnalysisResult result =
                service.run(USER_ID, CONNECTION_ID, List.of(1L, 2L), List.of(1L, 2L));

        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.payload().repoSummaries()).hasSize(1);
    }

    @Test
    @DisplayName("metadata에 language만 있고 활동 근거가 없으면 repo summary 없이 성공 저장하지 않는다")
    void run_noActivityMetadata_throwsAnalysisFailedBeforeSynthesis() {
        primeConnectionAndProjects(makeProject(1L, "user/a", "Java", "{}"));
        given(metadataFetcher.fetch(anyString(), anyString(), anyString(), anyString()))
                .willReturn(languageOnlyMetadata());

        assertThatThrownBy(() -> service.run(USER_ID, CONNECTION_ID, List.of(1L), List.of(1L)))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ANALYSIS_FAILED);
        verify(analysisRepo, never()).save(any(GithubAnalysis.class));
    }

    private void primeRepos() {
        primeConnectionAndProjects(makeProject(1L, "user/a", "Java", "{}"));
        given(metadataFetcher.fetch(anyString(), anyString(), anyString(), anyString()))
                .willReturn(sampleMetadata());
    }

    private void primeConnectionAndProjects(GithubProject... projects) {
        GithubConnection connection = GithubConnection.connect(
                USER_ID, "42", "user", GithubAccessType.OAUTH, "token-abc");
        ReflectionTestUtils.setField(connection, "id", CONNECTION_ID);
        given(connectionRepo.findByIdAndUserId(CONNECTION_ID, USER_ID)).willReturn(Optional.of(connection));
        given(connectionRepo.existsByIdAndUserId(CONNECTION_ID, USER_ID)).willReturn(true);
        given(projectRepo.findByUserIdAndGithubConnectionId(USER_ID, CONNECTION_ID))
                .willReturn(List.of(projects));
    }

    private static RepoMetadata sampleMetadata() {
        RepoMetadata.CommitItem commit = new RepoMetadata.CommitItem(
                "abc", "feat: OAuth", "", List.of("X.java"), 10, 0, "diff body");
        return new RepoMetadata(null, Map.of("Java", 1000L), List.of(),
                List.of(commit), List.of(), List.of());
    }

    private static RepoMetadata emptyMetadata() {
        return new RepoMetadata(null, Map.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static RepoMetadata languageOnlyMetadata() {
        return new RepoMetadata(null, Map.of("Java", 1000L), List.of(), List.of(), List.of(), List.of());
    }

    private void primeLlm() {
        given(llmClient.complete(anyString(), anyString()))
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

    private static TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        });
    }
}
