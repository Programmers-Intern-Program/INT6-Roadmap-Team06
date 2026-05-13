package com.back.coach.domain.portfolio.service;

import com.back.coach.domain.github.dto.GithubAnalysisPayload;
import com.back.coach.domain.github.service.GithubAnalysisPayloadJson;
import com.back.coach.domain.portfolio.dto.PortfolioDraftDetailResponse;
import com.back.coach.domain.portfolio.dto.PortfolioDraftPayload;
import com.back.coach.domain.portfolio.dto.PortfolioDraftUpdateRequest;
import com.back.coach.domain.portfolio.dto.PortfolioDraftVariant;
import com.back.coach.domain.portfolio.entity.PortfolioDraft;
import com.back.coach.domain.portfolio.repository.PortfolioDraftRepository;
import com.back.coach.domain.user.entity.User;
import com.back.coach.domain.user.repository.UserRepository;
import com.back.coach.external.llm.LlmClient;
import com.back.coach.global.code.AuthProvider;
import com.back.coach.global.code.GithubDepthLevel;
import com.back.coach.global.code.GithubEvidenceType;
import com.back.coach.global.code.HighlightStatus;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.back.coach.support.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@IntegrationTest
class PortfolioDraftServiceIntegrationTest {

    @Autowired
    private PortfolioDraftService portfolioDraftService;

    @Autowired
    private PortfolioDraftRepository portfolioDraftRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GithubAnalysisPayloadJson githubAnalysisPayloadJson;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private LlmClient llmClient;

    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long userId : userIds) {
            jdbc.update("DELETE FROM portfolio_drafts WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM coach_conversations WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM chat_sessions WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM progress_logs WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM roadmap_weeks WHERE roadmap_id IN (SELECT id FROM learning_roadmaps WHERE user_id = ?)", userId);
            jdbc.update("DELETE FROM learning_roadmaps WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM capability_diagnoses WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM github_analyses WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM github_connections WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM user_profiles WHERE user_id = ?", userId);
            userRepository.deleteById(userId);
        }
        jdbc.update("DELETE FROM job_roles WHERE role_code LIKE 'TEST_PORTFOLIO_%'");
    }

    @Test
    @DisplayName("createDraft: LLM 호출 없이 3개 빈 초안 틀과 sourceRefs를 저장한다")
    void createDraftSavesEmptyVariantsWithoutLlmCall() {
        Long userId = createUser();
        PortfolioFixture fixture = seedPortfolioFixture(userId);

        PortfolioDraftDetailResponse response = portfolioDraftService.createDraft(userId);

        assertThat(response.title()).isEqualTo("포트폴리오 초안");
        assertThat(response.draftPayload().format()).isEqualTo("PROJECT_WRITEUP");
        assertThat(response.draftPayload().variants())
                .extracting(PortfolioDraftVariant::key)
                .containsExactly("DONE", "DONE_IN_PROGRESS", "ALL");
        assertThat(response.draftPayload().variants())
                .allSatisfy(variant -> {
                    assertThat(variant.content()).isEmpty();
                    assertThat(variant.generated()).isFalse();
                });

        assertThat(portfolioDraftService.listDrafts(userId)).hasSize(1);
        assertThat(refObjects(response, "githubAnalyses"))
                .extracting(node -> String.valueOf(node.get("githubAnalysisId")))
                .contains(String.valueOf(fixture.latestGithubAnalysisId()), String.valueOf(fixture.oldGithubAnalysisId()));
        assertThat(refStrings(response, "roadmapWeekIds"))
                .contains(String.valueOf(fixture.doneWeekId()), String.valueOf(fixture.inProgressWeekId()), String.valueOf(fixture.plannedWeekId()));
        assertThat(refStrings(response, "progressLogIds"))
                .contains(String.valueOf(fixture.doneProgressLogId()), String.valueOf(fixture.inProgressProgressLogId()));
        assertThat(refStrings(response, "coachConversationIds"))
                .contains(String.valueOf(fixture.userConversationId()), String.valueOf(fixture.coachConversationId()));
        verify(llmClient, never()).complete(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("generateVariant: DONE은 완료 근거만 prompt에 넣고 해당 탭만 생성한다")
    void generateVariantDoneUsesOnlyDoneEvidence() {
        Long userId = createUser();
        seedPortfolioFixture(userId);
        PortfolioDraftDetailResponse draft = portfolioDraftService.createDraft(userId);
        given(llmClient.complete(anyString(), anyString(), anyInt())).willReturn(singleVariantResponse(
                "완료 기반 포트폴리오 초안",
                "Redis 공식 문서 정리를 통해 캐시 적용 전 기준을 정리했습니다."
        ));

        PortfolioDraftDetailResponse response =
                portfolioDraftService.generateVariant(userId, Long.valueOf(draft.draftId()), "DONE");

        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> maxTokensCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(llmClient).complete(systemCaptor.capture(), userPromptCaptor.capture(), maxTokensCaptor.capture());
        assertThat(systemCaptor.getValue()).contains("JSON-only output assistant for Korean users");
        assertThat(systemCaptor.getValue()).contains("coachConversationCandidates.userMemos");
        assertThat(systemCaptor.getValue()).contains("Do not invent URLs");
        assertThat(systemCaptor.getValue()).contains("sections");
        assertThat(systemCaptor.getValue()).contains("Do not output fields named variants");
        assertThat(systemCaptor.getValue()).contains("project write-up");
        assertThat(systemCaptor.getValue()).contains("not a study checklist");
        assertThat(systemCaptor.getValue()).contains("paragraph strings");
        assertThat(systemCaptor.getValue()).contains("Never convert IN_PROGRESS or TODO/planned records into completed facts");
        assertThat(systemCaptor.getValue()).contains("Do not claim performance improvement");
        assertThat(maxTokensCaptor.getValue()).isEqualTo(6000);

        String prompt = userPromptCaptor.getValue();
        assertThat(prompt).contains("Create only the DONE portfolio draft variant");
        assertThat(prompt).contains("project write-up draft");
        assertThat(prompt).contains("not a study checklist");
        assertThat(prompt).contains("Redis 공식 문서 정리 완료");
        assertThat(prompt).doesNotContain("캐시 예제 진행 중");
        assertThat(prompt).doesNotContain("장애 대응 회고 작성");
        assertThat(prompt).doesNotContain("아직 시작하지 않은 모니터링 개선");
        assertThat(prompt).doesNotContain("Coach가 추천한 다음 프로젝트 후보");
        assertThat(prompt).contains("최신 Spring API 개선 요약");
        assertThat(prompt).doesNotContain("오래된 Spring API 요약");

        String doneContent = response.draftPayload().variants().get(0).content();
        String inProgressContent = response.draftPayload().variants().get(1).content();
        String allContent = response.draftPayload().variants().get(2).content();
        assertThat(doneContent)
                .contains("## 개요\nRedis 공식 문서 정리를 통해", "## 구현 내용", "## 기술적 판단과 배운 점");
        assertThat(response.draftPayload().variants().get(0).generated()).isTrue();
        assertThat(inProgressContent).isEmpty();
        assertThat(allContent).isEmpty();
    }

    @Test
    @DisplayName("generateVariant: DONE_IN_PROGRESS는 완료와 진행 중 근거만 사용한다")
    void generateVariantDoneInProgressExcludesPlannedAndCoachRecommendations() {
        Long userId = createUser();
        seedPortfolioFixture(userId);
        PortfolioDraftDetailResponse draft = portfolioDraftService.createDraft(userId);
        given(llmClient.complete(anyString(), anyString(), anyInt())).willReturn(singleVariantResponse(
                "진행 중 포함 포트폴리오 초안",
                "완료한 Redis 정리와 캐시 예제 진행 중 기록을 연결했습니다."
        ));

        PortfolioDraftDetailResponse response =
                portfolioDraftService.generateVariant(userId, Long.valueOf(draft.draftId()), "DONE_IN_PROGRESS");

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmClient).complete(anyString(), userPromptCaptor.capture(), anyInt());
        String prompt = userPromptCaptor.getValue();
        assertThat(prompt).contains("Create only the DONE_IN_PROGRESS portfolio draft variant");
        assertThat(prompt).contains("Redis 공식 문서 정리 완료");
        assertThat(prompt).contains("캐시 예제 진행 중");
        assertThat(prompt).doesNotContain("아직 시작하지 않은 모니터링 개선");
        assertThat(prompt).doesNotContain("Coach가 추천한 다음 프로젝트 후보");

        assertThat(response.draftPayload().variants().get(0).content()).isEmpty();
        assertThat(response.draftPayload().variants().get(1).content())
                .contains("## 개요\n완료한 Redis 정리와 캐시 예제 진행 중 기록");
        assertThat(response.draftPayload().variants().get(1).generated()).isTrue();
        assertThat(response.draftPayload().variants().get(2).content()).isEmpty();
    }

    @Test
    @DisplayName("generateVariant: ALL은 예정과 Coach 추천을 미래 계획 근거로 포함한다")
    void generateVariantAllIncludesPlannedAndCoachRecommendationsAsFuturePlans() {
        Long userId = createUser();
        seedPortfolioFixture(userId);
        PortfolioDraftDetailResponse draft = portfolioDraftService.createDraft(userId);
        given(llmClient.complete(anyString(), anyString(), anyInt())).willReturn(singleVariantResponse(
                "전체 계획 포트폴리오 초안",
                "완료, 진행 중, 예정 후보를 구분해 운영 백엔드 성장 흐름으로 정리했습니다."
        ));

        PortfolioDraftDetailResponse response =
                portfolioDraftService.generateVariant(userId, Long.valueOf(draft.draftId()), "ALL");

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmClient).complete(anyString(), userPromptCaptor.capture(), anyInt());
        String prompt = userPromptCaptor.getValue();
        assertThat(prompt).contains("Create only the ALL portfolio draft variant");
        assertThat(prompt).contains("Planned and recommendation items must be future plans only");
        assertThat(prompt).contains("Redis 공식 문서 정리 완료");
        assertThat(prompt).contains("캐시 예제 진행 중");
        assertThat(prompt).contains("아직 시작하지 않은 모니터링 개선");
        assertThat(prompt).contains("Coach가 추천한 다음 프로젝트 후보");

        assertThat(response.draftPayload().variants().get(0).content()).isEmpty();
        assertThat(response.draftPayload().variants().get(1).content()).isEmpty();
        assertThat(response.draftPayload().variants().get(2).content())
                .contains("## 개요\n완료, 진행 중, 예정 후보를 구분");
        assertThat(response.draftPayload().variants().get(2).generated()).isTrue();
    }

    @Test
    @DisplayName("generateVariant: 이미 생성된 탭과 다른 탭의 편집 내용은 덮어쓰지 않는다")
    void generateVariantDoesNotOverwriteExistingVariantContent() {
        Long userId = createUser();
        seedPortfolioFixture(userId);
        PortfolioDraftDetailResponse draft = portfolioDraftService.createDraft(userId);
        PortfolioDraftPayload editedPayload = new PortfolioDraftPayload(
                "PROJECT_WRITEUP",
                List.of(
                        new PortfolioDraftVariant("DONE", "완료 기반", "사용자가 직접 쓴 완료 초안", true),
                        new PortfolioDraftVariant("DONE_IN_PROGRESS", "완료 + 진행 중", "", false),
                        new PortfolioDraftVariant("ALL", "전체 계획 포함", "전체 탭 편집 내용", true)
                )
        );
        portfolioDraftService.updateDraft(userId, Long.valueOf(draft.draftId()),
                new PortfolioDraftUpdateRequest(draft.title(), editedPayload));
        given(llmClient.complete(anyString(), anyString(), anyInt())).willReturn(singleVariantResponse(
                "진행 중 포함 포트폴리오 초안",
                "진행 중 탭 새 내용"
        ));

        PortfolioDraftDetailResponse response =
                portfolioDraftService.generateVariant(userId, Long.valueOf(draft.draftId()), "DONE_IN_PROGRESS");

        assertThat(response.draftPayload().variants())
                .extracting(PortfolioDraftVariant::content)
                .containsExactly(
                        "사용자가 직접 쓴 완료 초안",
                        response.draftPayload().variants().get(1).content(),
                        "전체 탭 편집 내용"
                );
        assertThat(response.draftPayload().variants().get(1).content())
                .contains("진행 중 탭 새 내용");

        PortfolioDraftDetailResponse noOverwrite =
                portfolioDraftService.generateVariant(userId, Long.valueOf(draft.draftId()), "DONE");
        assertThat(noOverwrite.draftPayload().variants().get(0).content())
                .isEqualTo("사용자가 직접 쓴 완료 초안");
        verify(llmClient).complete(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("updateDraft: 사용자가 편집한 제목과 payload를 저장한다")
    void updateDraftUpdatesPayload() throws Exception {
        Long userId = createUser();
        PortfolioDraft draft = portfolioDraftRepository.save(PortfolioDraft.create(
                userId,
                "초기 초안",
                objectMapper.writeValueAsString(new PortfolioDraftPayload(
                        "PROJECT_WRITEUP",
                        List.of(new PortfolioDraftVariant("DONE", "완료 기반", "초기 내용"))
                )),
                "{}"
        ));
        PortfolioDraftPayload editedPayload = new PortfolioDraftPayload(
                "PROJECT_WRITEUP",
                List.of(
                        new PortfolioDraftVariant("DONE", "완료 기반", "수정된 완료 내용"),
                        new PortfolioDraftVariant("DONE_IN_PROGRESS", "완료 + 진행 중", "수정된 진행 내용"),
                        new PortfolioDraftVariant("ALL", "전체 계획 포함", "수정된 전체 내용")
                )
        );

        PortfolioDraftDetailResponse response = portfolioDraftService.updateDraft(
                userId,
                draft.getId(),
                new PortfolioDraftUpdateRequest("수정된 초안", editedPayload)
        );

        assertThat(response.title()).isEqualTo("수정된 초안");
        assertThat(response.draftPayload().variants())
                .extracting(PortfolioDraftVariant::content)
                .contains("수정된 완료 내용", "수정된 진행 내용", "수정된 전체 내용");
    }

    @Test
    @DisplayName("generateVariant: LLM 응답이 깨져도 해당 탭만 fallback 초안을 저장한다")
    void generateVariantFallsBackWhenLlmResponseIsInvalid() {
        Long userId = createUser();
        seedPortfolioFixture(userId);
        PortfolioDraftDetailResponse draft = portfolioDraftService.createDraft(userId);
        given(llmClient.complete(anyString(), anyString(), anyInt()))
                .willThrow(new ServiceException(ErrorCode.LLM_INVALID_RESPONSE));

        PortfolioDraftDetailResponse response =
                portfolioDraftService.generateVariant(userId, Long.valueOf(draft.draftId()), "ALL");

        assertThat(response.title()).isEqualTo("로드맵 기반 포트폴리오 초안");
        assertThat(response.draftPayload().variants())
                .extracting(PortfolioDraftVariant::key)
                .containsExactly("DONE", "DONE_IN_PROGRESS", "ALL");
        assertThat(response.draftPayload().variants().get(0).content())
                .isEmpty();
        assertThat(response.draftPayload().variants().get(1).content())
                .isEmpty();
        assertThat(response.draftPayload().variants().get(2).content())
                .contains("## 구현 내용", "## 기술적 판단과 배운 점")
                .contains("Redis 공식 문서 정리 완료")
                .contains("캐시 예제 진행 중")
                .contains("아직 시작하지 않은 모니터링 개선")
                .contains("Coach가 추천한 다음 프로젝트 후보");
    }

    @Test
    @DisplayName("generateVariant: LLM section 일부가 비어도 markdown 섹션을 안전하게 조립한다")
    void generateVariantRendersEmptySectionsWithPlaceholder() {
        Long userId = createUser();
        seedPortfolioFixture(userId);
        PortfolioDraftDetailResponse draft = portfolioDraftService.createDraft(userId);
        given(llmClient.complete(anyString(), anyString(), anyInt())).willReturn(partialSectionsResponse());

        PortfolioDraftDetailResponse response =
                portfolioDraftService.generateVariant(userId, Long.valueOf(draft.draftId()), "DONE");

        assertThat(response.draftPayload().variants())
                .extracting(PortfolioDraftVariant::key)
                .containsExactly("DONE", "DONE_IN_PROGRESS", "ALL");
        assertThat(response.draftPayload().variants().get(0).content())
                .contains("## 개요\n완료한 Redis 문서 정리를 중심으로 기술합니다.")
                .contains("## 문제/목표\n작성 가능한 근거가 없습니다.")
                .contains("## 구현 내용\n- 작성 가능한 근거가 없습니다.")
                .doesNotContain("## 개요\n-");
        assertThat(response.draftPayload().variants().get(1).content()).isEmpty();
        assertThat(response.draftPayload().variants().get(2).content()).isEmpty();
    }

    @Test
    @DisplayName("generateVariant: 지원하지 않는 variantKey는 INVALID_INPUT")
    void generateVariantRejectsInvalidVariantKey() {
        Long userId = createUser();
        PortfolioDraftDetailResponse draft = portfolioDraftService.createDraft(userId);

        assertThatThrownBy(() -> portfolioDraftService.generateVariant(userId, Long.valueOf(draft.draftId()), "UNKNOWN"))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("getDraft: 다른 사용자의 초안은 조회할 수 없다")
    void getDraftRejectsOtherUser() throws Exception {
        Long ownerId = createUser();
        Long otherUserId = createUser();
        PortfolioDraft draft = portfolioDraftRepository.save(PortfolioDraft.create(
                ownerId,
                "소유자 초안",
                objectMapper.writeValueAsString(new PortfolioDraftPayload(
                        "PROJECT_WRITEUP",
                        List.of(new PortfolioDraftVariant("DONE", "완료 기반", "내용"))
                )),
                "{}"
        ));

        assertThatThrownBy(() -> portfolioDraftService.getDraft(otherUserId, draft.getId()))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("generateVariant: 다른 사용자의 초안은 생성할 수 없다")
    void generateVariantRejectsOtherUser() throws Exception {
        Long ownerId = createUser();
        Long otherUserId = createUser();
        PortfolioDraft draft = portfolioDraftRepository.save(PortfolioDraft.create(
                ownerId,
                "소유자 초안",
                objectMapper.writeValueAsString(new PortfolioDraftPayload(
                        "PROJECT_WRITEUP",
                        List.of(new PortfolioDraftVariant("DONE", "완료 기반", "", false))
                )),
                "{}"
        ));

        assertThatThrownBy(() -> portfolioDraftService.generateVariant(otherUserId, draft.getId(), "DONE"))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
    }

    private Long createUser() {
        User user = userRepository.save(User.signupFromOAuth(
                AuthProvider.GITHUB,
                "portfolio-gh-" + UUID.randomUUID(),
                "portfolio-" + UUID.randomUUID() + "@test.com"
        ));
        userIds.add(user.getId());
        return user.getId();
    }

    private PortfolioFixture seedPortfolioFixture(Long userId) {
        String suffix = String.valueOf(System.nanoTime());
        Long jobRoleId = jdbc.queryForObject("""
                INSERT INTO job_roles (role_code, role_name, description)
                VALUES (?, ?, 'test')
                RETURNING id
                """, Long.class, "TEST_PORTFOLIO_" + suffix, "포트폴리오 테스트 " + suffix);
        Long profileId = jdbc.queryForObject("""
                INSERT INTO user_profiles (user_id, job_role_id, current_level, weekly_study_hours, interest_areas_json)
                VALUES (?, ?, 'BASIC', 8, ?::jsonb)
                RETURNING id
                """, Long.class, userId, jobRoleId, "[\"Java\", \"Spring\"]");
        Long connectionId = jdbc.queryForObject("""
                INSERT INTO github_connections (user_id, github_user_id, github_login, access_type)
                VALUES (?, ?, ?, 'OAUTH')
                RETURNING id
                """, Long.class, userId, "portfolio-" + suffix, "portfolio-" + suffix);
        Long oldGithubAnalysisId = insertGithubAnalysis(
                userId,
                connectionId,
                1,
                "오래된 GitHub 분석",
                githubPayload("오래된 Spring API 요약"),
                Instant.parse("2026-05-10T00:00:00Z")
        );
        Long latestGithubAnalysisId = insertGithubAnalysis(
                userId,
                connectionId,
                2,
                "최신 GitHub 분석",
                githubPayload("최신 Spring API 개선 요약"),
                Instant.parse("2026-05-11T00:00:00Z")
        );
        Long diagnosisId = jdbc.queryForObject("""
                INSERT INTO capability_diagnoses (
                    user_id, profile_id, github_analysis_id, job_role_id, version, current_level, summary, diagnosis_payload
                )
                VALUES (?, ?, ?, ?, 1, 'BASIC', 'Redis 보완 필요', ?::jsonb)
                RETURNING id
                """, Long.class, userId, profileId, latestGithubAnalysisId, jobRoleId, "{}");
        Long oldRoadmapId = insertRoadmap(userId, diagnosisId, 1, "Redis 기초 로드맵", Instant.parse("2026-05-10T01:00:00Z"));
        Long latestRoadmapId = insertRoadmap(userId, diagnosisId, 2, "운영 백엔드 로드맵", Instant.parse("2026-05-11T01:00:00Z"));
        Long doneWeekId = insertWeek(
                oldRoadmapId,
                1,
                "Redis 기초",
                "캐시 설계 기반을 먼저 잡기 위함",
                "[{\"title\":\"Redis 공식 문서 읽기\",\"type\":\"READ_DOCS\"}]",
                "[{\"title\":\"Redis Documentation\",\"type\":\"DOCS\",\"url\":\"https://redis.io/docs\"}]"
        );
        Long inProgressWeekId = insertWeek(
                latestRoadmapId,
                1,
                "Spring 캐시 적용",
                "실제 API에 캐시를 적용해 보기 위함",
                "[{\"title\":\"캐시 예제 구현\",\"type\":\"BUILD_EXAMPLE\"}]",
                "[]"
        );
        Long todoWeekId = insertWeek(
                latestRoadmapId,
                2,
                "장애 대응 회고 작성",
                "운영 관점의 학습 결과를 정리하기 위함",
                "[{\"title\":\"장애 대응 회고 작성\",\"type\":\"WRITE_NOTE\"}]",
                "[]"
        );
        Long plannedWeekId = insertWeek(
                latestRoadmapId,
                3,
                "아직 시작하지 않은 모니터링 개선",
                "다음 개선 후보를 남기기 위함",
                "[{\"title\":\"대시보드 지표 정리\",\"type\":\"MINI_PROJECT\"}]",
                "[]"
        );
        Long doneProgressLogId = insertProgress(userId, doneWeekId, "DONE", "Redis 공식 문서 정리 완료", Instant.parse("2026-05-11T02:00:00Z"));
        Long inProgressProgressLogId = insertProgress(userId, inProgressWeekId, "IN_PROGRESS", "캐시 예제 진행 중", Instant.parse("2026-05-11T03:00:00Z"));
        insertProgress(userId, todoWeekId, "TODO", "회고는 아직 작성 전", Instant.parse("2026-05-11T04:00:00Z"));
        Long sessionId = jdbc.queryForObject("""
                INSERT INTO chat_sessions (user_id, profile_version, roadmap_version, status, started_at)
                VALUES (?, 1, 1, 'ACTIVE', ?)
                RETURNING id
                """, Long.class, userId, ts(Instant.parse("2026-05-11T05:00:00Z")));
        Long userConversationId = insertConversation(
                sessionId,
                userId,
                "USER",
                "사용자가 직접 말한 학습 메모 후보",
                null,
                null,
                Instant.parse("2026-05-11T06:00:00Z")
        );
        Long coachConversationId = insertConversation(
                sessionId,
                userId,
                "COACH",
                "Coach가 추천한 다음 프로젝트 후보",
                "SIMPLE_GUIDE",
                "TASK_BREAKDOWN",
                Instant.parse("2026-05-11T07:00:00Z")
        );
        return new PortfolioFixture(
                oldGithubAnalysisId,
                latestGithubAnalysisId,
                doneWeekId,
                inProgressWeekId,
                plannedWeekId,
                doneProgressLogId,
                inProgressProgressLogId,
                userConversationId,
                coachConversationId
        );
    }

    private Long insertGithubAnalysis(
            Long userId,
            Long connectionId,
            int version,
            String summary,
            GithubAnalysisPayload payload,
            Instant createdAt
    ) {
        return jdbc.queryForObject("""
                INSERT INTO github_analyses (user_id, github_connection_id, version, summary, analysis_payload, created_at)
                VALUES (?, ?, ?, ?, ?::jsonb, ?)
                RETURNING id
                """, Long.class, userId, connectionId, version, summary, githubAnalysisPayloadJson.toJson(payload), ts(createdAt));
    }

    private Long insertRoadmap(Long userId, Long diagnosisId, int version, String summary, Instant createdAt) {
        return jdbc.queryForObject("""
                INSERT INTO learning_roadmaps (user_id, diagnosis_id, version, total_weeks, summary, roadmap_payload, created_at)
                VALUES (?, ?, ?, 3, ?, ?::jsonb, ?)
                RETURNING id
                """, Long.class, userId, diagnosisId, version, summary, "{}", ts(createdAt));
    }

    private Long insertWeek(
            Long roadmapId,
            int weekNumber,
            String topic,
            String reason,
            String tasksJson,
            String materialsJson
    ) {
        return jdbc.queryForObject("""
                INSERT INTO roadmap_weeks (
                    roadmap_id, week_number, topic, reason_text, tasks_json, materials_json, estimated_hours
                )
                VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, 4.0)
                RETURNING id
                """, Long.class, roadmapId, weekNumber, topic, reason, tasksJson, materialsJson);
    }

    private Long insertProgress(Long userId, Long weekId, String status, String note, Instant createdAt) {
        Timestamp completedAt = "DONE".equals(status) ? ts(createdAt) : null;
        return jdbc.queryForObject("""
                INSERT INTO progress_logs (user_id, roadmap_week_id, status, note, completed_at, created_at)
                VALUES (?, ?, ?, ?, ?::timestamptz, ?)
                RETURNING id
                """, Long.class, userId, weekId, status, note, completedAt, ts(createdAt));
    }

    private Long insertConversation(
            Long sessionId,
            Long userId,
            String role,
            String messageText,
            String route,
            String detectedIntent,
            Instant createdAt
    ) {
        return jdbc.queryForObject("""
                INSERT INTO coach_conversations (session_id, user_id, role, message_text, route, detected_intent, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, sessionId, userId, role, messageText, route, detectedIntent, ts(createdAt));
    }

    private GithubAnalysisPayload githubPayload(String repoSummary) {
        return new GithubAnalysisPayload(
                new GithubAnalysisPayload.StaticSignals(
                        List.of(new GithubAnalysisPayload.PrimaryLanguage("Java", 1.0)),
                        1,
                        "WEEKLY",
                        "CONSISTENT"
                ),
                List.of(new GithubAnalysisPayload.RepoSummary(
                        "repo-1",
                        "user/demo-api",
                        repoSummary,
                        List.of(new GithubAnalysisPayload.Highlight("Spring Boot API", HighlightStatus.ADOPTED))
                )),
                List.of(new GithubAnalysisPayload.TechTag("Spring Boot", "API 구현에 반복적으로 사용")),
                List.of(new GithubAnalysisPayload.DepthEstimate("Spring Boot", GithubDepthLevel.APPLIED, "Controller와 JPA 사용")),
                List.of(new GithubAnalysisPayload.GithubEvidence(
                        "user/demo-api",
                        GithubEvidenceType.CODE,
                        "src/main/java",
                        "Controller와 Service 계층 구현 근거"
                )),
                List.of(),
                new GithubAnalysisPayload.FinalTechProfile(
                        List.of("Java", "Spring Boot"),
                        List.of("Redis", "운영 안정성")
                )
        );
    }

    private Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    private String singleVariantResponse(String title, String overview) {
        return """
                {
                  "title": "%s",
                  "sections": {
                    "overview": ["%s"],
                    "problemGoal": ["로드맵 진도를 프로젝트 기술서 맥락으로 재구성하는 것이 목표입니다."],
                    "studyAndImplementation": ["선택한 초안 버전의 근거만 사용해 구현 경험 후보를 정리했습니다."],
                    "techStack": ["Java", "Spring Boot"],
                    "lessons": ["GitHub 분석 근거를 함께 보며 기술 선택과 다음 개선 지점을 분리했습니다."],
                    "nextImprovements": ["아직 확정되지 않은 항목은 다음 개선 후보로 남깁니다."],
                    "memoCandidates": ["학습 메모 후보는 완료 사실이 아닌 보조 자료로 둡니다."],
                    "recommendationCandidates": ["추천 후보는 미래 계획으로만 정리합니다."],
                    "sourceSummary": ["선택한 variant 범위의 근거만 사용했습니다."]
                  }
                }
                """.formatted(title, overview);
    }

    private String generationResponse() {
        return """
                {
                  "title": "백엔드 성장 포트폴리오 초안",
                  "sectionsByVariant": {
                    "DONE": {
                      "overview": ["Redis 공식 문서 정리를 통해 캐시 적용 전 필요한 명령어와 데이터 만료 기준을 정리하고, 이를 백엔드 API 개선 후보로 연결한 프로젝트 기술서 초안입니다."],
                      "problemGoal": ["캐시 설계 기반이 부족했던 문제를 공식 문서 기반으로 보완하고, 이후 실제 API 적용 단계에서 판단 기준으로 사용할 수 있게 정리하는 것이 목표입니다."],
                      "studyAndImplementation": ["Redis 공식 문서 정리 완료를 바탕으로 캐시 적용 전 데이터 구조와 명령어 사용 기준을 정리했습니다."],
                      "techStack": ["Java", "Spring Boot"],
                      "lessons": ["최신 Spring API 개선 요약을 GitHub 근거로 확인했으며, 캐시를 적용하기 전에는 저장된 코드 구조와 데이터 흐름을 먼저 설명할 수 있어야 한다는 점을 정리했습니다."],
                      "nextImprovements": ["완료 근거만 포함하므로 예정 작업은 제외합니다."],
                      "memoCandidates": ["사용자가 직접 말한 학습 메모 후보"],
                      "recommendationCandidates": ["완료 기반 버전에서는 추천 후보를 완료 사실로 쓰지 않습니다."],
                      "sourceSummary": ["progress_logs와 github_analyses를 근거로 사용했습니다."]
                    },
                    "DONE_IN_PROGRESS": {
                      "overview": ["완료한 Redis 정리와 캐시 예제 진행 중 기록을 연결해 문서 학습에서 실제 API 적용으로 확장하는 과정을 프로젝트 경험 후보로 작성합니다."],
                      "problemGoal": ["문서로 정리한 캐시 개념을 Spring Boot API에 적용하며 동작 확인과 구현 기준을 함께 남기는 것이 목표입니다."],
                      "studyAndImplementation": ["Redis 공식 문서 정리 완료 내용을 기준으로 캐시 예제 진행 중인 작업을 API 개선 후보로 연결했습니다."],
                      "techStack": ["Java", "Spring Boot"],
                      "lessons": ["Controller와 Service 계층 구현 근거를 함께 보면 캐시 적용은 단순 명령어 사용보다 요청 흐름과 책임 분리를 먼저 확인해야 한다는 점이 드러납니다."],
                      "nextImprovements": ["진행 중 작업을 마무리한 뒤 회고로 연결합니다."],
                      "memoCandidates": ["사용자가 직접 말한 학습 메모 후보"],
                      "recommendationCandidates": ["진행 중 버전에서는 예정 후보를 완료 사실로 쓰지 않습니다."],
                      "sourceSummary": ["progress_logs의 DONE과 IN_PROGRESS를 근거로 사용했습니다."]
                    },
                    "ALL": {
                      "overview": ["완료, 진행 중, 예정 후보를 한 문서에 묶되 완료 사실과 다음 개선 후보를 구분해 운영 백엔드 성장 흐름으로 작성합니다."],
                      "problemGoal": ["캐시 적용 경험을 운영 안정성 관점의 포트폴리오 산출물로 확장하고, 아직 시작하지 않은 항목은 다음 개선 후보로 분리하는 것이 목표입니다."],
                      "studyAndImplementation": ["Redis 공식 문서 정리 완료와 캐시 예제 진행 중 기록을 현재 구현 근거로 사용합니다."],
                      "techStack": ["Java", "Spring Boot", "Redis"],
                      "lessons": ["최신 Spring API 개선 요약을 기준으로 캐시 적용과 모니터링 개선은 별도 단계로 관리해야 한다는 기술적 판단을 남깁니다."],
                      "nextImprovements": ["아직 시작하지 않은 모니터링 개선은 완료 사실이 아니라 다음 개선 후보로 정리합니다."],
                      "memoCandidates": ["사용자가 직접 말한 학습 메모 후보"],
                      "recommendationCandidates": ["Coach가 추천한 다음 프로젝트 후보"],
                      "sourceSummary": ["로드맵 진도, GitHub 분석, Coach 후보를 근거로 사용했습니다."]
                    }
                  }
                }
                """;
    }

    private String partialSectionsResponse() {
        return """
                {
                  "title": "부분 section 포트폴리오 초안",
                  "sections": {
                    "overview": ["완료한 Redis 문서 정리를 중심으로 기술합니다."]
                  }
                }
                """;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> refObjects(PortfolioDraftDetailResponse response, String key) {
        return (List<Map<String, Object>>) response.sourceRefs().getOrDefault(key, List.of());
    }

    @SuppressWarnings("unchecked")
    private List<String> refStrings(PortfolioDraftDetailResponse response, String key) {
        return (List<String>) response.sourceRefs().getOrDefault(key, List.of());
    }

    private record PortfolioFixture(
            Long oldGithubAnalysisId,
            Long latestGithubAnalysisId,
            Long doneWeekId,
            Long inProgressWeekId,
            Long plannedWeekId,
            Long doneProgressLogId,
            Long inProgressProgressLogId,
            Long userConversationId,
            Long coachConversationId
    ) {
    }
}
