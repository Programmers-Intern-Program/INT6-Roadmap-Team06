package com.back.coach.domain.flow;

import com.back.coach.domain.dashboard.dto.DashboardSnapshot;
import com.back.coach.domain.dashboard.service.DashboardSnapshotService;
import com.back.coach.domain.diagnosis.dto.DiagnosisDetailResponse;
import com.back.coach.domain.diagnosis.dto.DiagnosisRequest;
import com.back.coach.domain.diagnosis.entity.CapabilityDiagnosis;
import com.back.coach.domain.diagnosis.repository.CapabilityDiagnosisRepository;
import com.back.coach.domain.diagnosis.service.DiagnosisCommandService;
import com.back.coach.domain.github.dto.GithubAnalysisPayload;
import com.back.coach.domain.github.entity.GithubAnalysis;
import com.back.coach.domain.github.entity.GithubConnection;
import com.back.coach.domain.github.repository.GithubAnalysisRepository;
import com.back.coach.domain.github.repository.GithubConnectionRepository;
import com.back.coach.domain.github.service.GithubAnalysisPayloadJson;
import com.back.coach.domain.roadmap.dto.RoadmapDetailResponse;
import com.back.coach.domain.roadmap.dto.RoadmapRequest;
import com.back.coach.domain.roadmap.entity.LearningRoadmap;
import com.back.coach.domain.roadmap.entity.ProgressLog;
import com.back.coach.domain.roadmap.entity.RoadmapWeek;
import com.back.coach.domain.roadmap.repository.LearningRoadmapRepository;
import com.back.coach.domain.roadmap.repository.ProgressLogRepository;
import com.back.coach.domain.roadmap.repository.RoadmapWeekRepository;
import com.back.coach.domain.roadmap.service.RoadmapCommandService;
import com.back.coach.domain.roadmap.service.RoadmapDetailSnapshotService;
import com.back.coach.domain.roadmap.service.RoadmapProgressCommandService;
import com.back.coach.domain.user.dto.ProfileSaveRequest;
import com.back.coach.domain.user.dto.ProfileSaveResult;
import com.back.coach.domain.user.dto.ProfileSkillRequest;
import com.back.coach.domain.user.entity.User;
import com.back.coach.domain.user.entity.UserSkill;
import com.back.coach.domain.user.repository.UserRepository;
import com.back.coach.domain.user.repository.UserSkillRepository;
import com.back.coach.domain.user.service.ProfileService;
import com.back.coach.external.llm.LlmClient;
import com.back.coach.global.code.AuthProvider;
import com.back.coach.global.code.CurrentLevel;
import com.back.coach.global.code.GithubAccessType;
import com.back.coach.global.code.ProgressStatus;
import com.back.coach.global.code.ProficiencyLevel;
import com.back.coach.global.code.SkillSourceType;
import com.back.coach.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@IntegrationTest
class V1ResultFlowIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSkillRepository userSkillRepository;

    @Autowired
    private GithubConnectionRepository githubConnectionRepository;

    @Autowired
    private GithubAnalysisRepository githubAnalysisRepository;

    @Autowired
    private CapabilityDiagnosisRepository capabilityDiagnosisRepository;

    @Autowired
    private LearningRoadmapRepository learningRoadmapRepository;

    @Autowired
    private RoadmapWeekRepository roadmapWeekRepository;

    @Autowired
    private ProgressLogRepository progressLogRepository;

    @Autowired
    private ProfileService profileService;

    @Autowired
    private DiagnosisCommandService diagnosisCommandService;

    @Autowired
    private RoadmapCommandService roadmapCommandService;

    @Autowired
    private RoadmapDetailSnapshotService roadmapDetailSnapshotService;

    @Autowired
    private RoadmapProgressCommandService roadmapProgressCommandService;

    @Autowired
    private DashboardSnapshotService dashboardSnapshotService;

    @Autowired
    private GithubAnalysisPayloadJson githubAnalysisPayloadJson;

    @MockitoBean
    private LlmClient llmClient;

    @Test
    @DisplayName("v1 결과 흐름이 DB 원본 기준으로 진단, 로드맵, 진도, 대시보드까지 이어진다")
    void v1ResultFlow_persistsVersionedResultsAndDashboardSnapshot() {
        given(llmClient.complete(anyString())).willReturn(diagnosisResponse(), roadmapResponse());

        User user = userRepository.save(User.signupFromOAuth(
                AuthProvider.GITHUB,
                unique("flow-gh"),
                unique("flow") + "@example.com"
        ));
        User otherUser = userRepository.save(User.signupFromOAuth(
                AuthProvider.GITHUB,
                unique("other-gh"),
                unique("other") + "@example.com"
        ));
        ProfileSaveResult profile = saveProfile(user.getId());
        GithubAnalysis githubAnalysis = saveGithubAnalysisFixture(user.getId());

        List<UserSkill> userInputSkills = userSkillRepository
                .findByUserIdAndSourceTypeOrderBySkillNameAsc(user.getId(), SkillSourceType.USER_INPUT);
        assertThat(userInputSkills)
                .extracting(UserSkill::getSkillName)
                .containsExactly("Spring Boot");

        DiagnosisDetailResponse diagnosis = diagnosisCommandService.createDiagnosis(
                user.getId(),
                new DiagnosisRequest(profile.profileId(), githubAnalysis.getId())
        );
        CapabilityDiagnosis persistedDiagnosis = capabilityDiagnosisRepository
                .findByIdAndUserId(Long.valueOf(diagnosis.diagnosisId()), user.getId())
                .orElseThrow();
        assertThat(persistedDiagnosis.getVersion()).isEqualTo(1);
        assertThat(persistedDiagnosis.getGithubAnalysisId()).isEqualTo(githubAnalysis.getId());
        assertThat(diagnosis.summary()).isEqualTo("Redis 보완 필요");

        RoadmapDetailResponse roadmap = roadmapCommandService.createRoadmap(
                user.getId(),
                new RoadmapRequest(Long.valueOf(diagnosis.diagnosisId()), githubAnalysis.getId(), null, 8, null)
        );
        LearningRoadmap persistedRoadmap = learningRoadmapRepository
                .findByIdAndUserId(Long.valueOf(roadmap.roadmapId()), user.getId())
                .orElseThrow();
        List<RoadmapWeek> persistedWeeks = roadmapWeekRepository
                .findByRoadmapIdOrderByWeekNumberAsc(persistedRoadmap.getId());
        assertThat(persistedRoadmap.getVersion()).isEqualTo(1);
        assertThat(persistedRoadmap.getDiagnosisId()).isEqualTo(persistedDiagnosis.getId());
        assertThat(persistedRoadmap.getRoadmapPayload()).doesNotContain("progressStatus");
        assertThat(persistedWeeks).hasSize(4);
        assertThat(roadmap.weeks())
                .extracting(RoadmapDetailResponse.WeekResponse::progressStatus)
                .containsExactly(
                        ProgressStatus.TODO,
                        ProgressStatus.TODO,
                        ProgressStatus.TODO,
                        ProgressStatus.TODO
                );

        Long roadmapId = Long.valueOf(roadmap.roadmapId());
        Long firstWeekId = Long.valueOf(roadmap.weeks().getFirst().roadmapWeekId());
        Long secondWeekId = Long.valueOf(roadmap.weeks().get(1).roadmapWeekId());
        Long thirdWeekId = Long.valueOf(roadmap.weeks().get(2).roadmapWeekId());

        roadmapProgressCommandService.appendProgress(
                user.getId(),
                roadmapId,
                firstWeekId,
                ProgressStatus.IN_PROGRESS,
                "Redis 공식 문서 읽기 시작"
        );
        roadmapProgressCommandService.appendProgress(
                user.getId(),
                roadmapId,
                firstWeekId,
                ProgressStatus.DONE,
                "Redis 공식 문서 정리 완료"
        );
        roadmapProgressCommandService.appendProgress(
                user.getId(),
                roadmapId,
                secondWeekId,
                ProgressStatus.IN_PROGRESS,
                "캐시 예제 진행 중"
        );
        roadmapProgressCommandService.appendProgress(
                user.getId(),
                roadmapId,
                thirdWeekId,
                ProgressStatus.SKIPPED,
                "이번 주는 후순위"
        );

        List<ProgressLog> firstWeekProgressLogs = progressLogRepository
                .findByUserIdAndRoadmapWeekIdOrderByCreatedAtDesc(user.getId(), firstWeekId);
        assertThat(firstWeekProgressLogs).hasSize(2);
        assertThat(firstWeekProgressLogs)
                .extracting(ProgressLog::getStatus)
                .containsExactly(ProgressStatus.DONE, ProgressStatus.IN_PROGRESS);
        assertThat(firstWeekProgressLogs)
                .extracting(ProgressLog::getNote)
                .containsExactly("Redis 공식 문서 정리 완료", "Redis 공식 문서 읽기 시작");

        RoadmapDetailResponse updatedRoadmap = RoadmapDetailResponse.from(
                roadmapDetailSnapshotService.findSnapshot(user.getId(), roadmapId),
                new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()
        );
        assertThat(updatedRoadmap.weeks())
                .extracting(RoadmapDetailResponse.WeekResponse::progressStatus)
                .containsExactly(
                        ProgressStatus.DONE,
                        ProgressStatus.IN_PROGRESS,
                        ProgressStatus.SKIPPED,
                        ProgressStatus.TODO
                );
        assertThat(updatedRoadmap.weeks().getFirst().progressNote())
                .isEqualTo("Redis 공식 문서 정리 완료");
        assertThat(updatedRoadmap.weeks().get(1).progressNote())
                .isEqualTo("캐시 예제 진행 중");
        assertThat(updatedRoadmap.weeks().get(2).progressNote())
                .isEqualTo("이번 주는 후순위");

        DashboardSnapshot dashboard = dashboardSnapshotService.findSnapshot(user.getId());
        assertThat(dashboard.profile().profileId()).isEqualTo(profile.profileId());
        assertThat(dashboard.githubAnalysis().githubAnalysisId()).isEqualTo(githubAnalysis.getId());
        assertThat(dashboard.diagnosis().diagnosisId()).isEqualTo(persistedDiagnosis.getId());
        assertThat(dashboard.roadmap().roadmapId()).isEqualTo(persistedRoadmap.getId());
        assertThat(dashboard.roadmap().progress())
                .isEqualTo(new DashboardSnapshot.ProgressSummary(4, 1, 1, 1, 1));

        DashboardSnapshot otherDashboard = dashboardSnapshotService.findSnapshot(otherUser.getId());
        assertThat(otherDashboard.profile()).isNull();
        assertThat(otherDashboard.githubAnalysis()).isNull();
        assertThat(otherDashboard.diagnosis()).isNull();
        assertThat(otherDashboard.roadmap()).isNull();
        verify(llmClient, times(2)).complete(anyString());
    }

    private ProfileSaveResult saveProfile(Long userId) {
        return profileService.saveProfile(userId, new ProfileSaveRequest(
                "BACKEND_DEVELOPER",
                CurrentLevel.JUNIOR,
                List.of(new ProfileSkillRequest("Spring Boot", ProficiencyLevel.WORKING)),
                List.of("Backend"),
                10,
                LocalDate.of(2099, 12, 31),
                null,
                null
        ));
    }

    private GithubAnalysis saveGithubAnalysisFixture(Long userId) {
        GithubConnection connection = githubConnectionRepository.save(GithubConnection.connect(
                userId,
                unique("ghu"),
                unique("login"),
                GithubAccessType.OAUTH,
                "ghp_test_token"
        ));
        return githubAnalysisRepository.save(GithubAnalysis.create(
                userId,
                connection.getId(),
                1,
                "Spring Boot 중심 GitHub 분석",
                githubAnalysisPayloadJson.toJson(githubAnalysisPayload())
        ));
    }

    private GithubAnalysisPayload githubAnalysisPayload() {
        return new GithubAnalysisPayload(
                new GithubAnalysisPayload.StaticSignals(
                        List.of(new GithubAnalysisPayload.PrimaryLanguage("Java", 1.0)),
                        1,
                        "WEEKLY",
                        "CONSISTENT"
                ),
                List.of(new GithubAnalysisPayload.RepoSummary(
                        "1",
                        "user/backend",
                        "Spring Boot API 구현 경험",
                        List.of(new GithubAnalysisPayload.Highlight("REST API", com.back.coach.global.code.HighlightStatus.ADOPTED),
                                new GithubAnalysisPayload.Highlight("JPA", com.back.coach.global.code.HighlightStatus.ADOPTED))
                )),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new GithubAnalysisPayload.FinalTechProfile(
                        List.of("Spring Boot"),
                        List.of("Backend")
                )
        );
    }

    private String diagnosisResponse() {
        return """
                {
                  "summary": "Redis 보완 필요",
                  "missingSkills": [
                    {
                      "skillName": "Redis",
                      "severity": "HIGH",
                      "reason": "캐시 설계 경험이 부족함",
                      "priorityOrder": 1
                    }
                  ],
                  "strengths": ["Spring Boot"],
                  "recommendations": ["Redis 캐시와 TTL 기반 설계를 먼저 학습"]
                }
                """;
    }

    private String roadmapResponse() {
        return """
                {
                  "summary": "Redis 중심 로드맵",
                  "weeks": [
                    {
                      "weekNumber": 1,
                      "topic": "Redis 기초",
                      "reason": "캐시 설계 역량을 먼저 보완해야 함",
                      "tasks": [
                        {
                          "type": "READ_DOCS",
                          "title": "Redis 공식 문서 읽기"
                        }
                      ],
                      "materials": [
                        {
                          "type": "DOCS",
                          "title": "Redis Documentation",
                          "url": "https://redis.io/docs"
                        }
                      ],
                      "estimatedHours": 8.0
                    },
                    {
                      "weekNumber": 2,
                      "topic": "Redis 적용",
                      "reason": "실제 프로젝트 적용 경험이 필요함",
                      "tasks": [
                        {
                          "type": "BUILD_EXAMPLE",
                          "title": "캐시 예제 구현"
                        }
                      ],
                      "materials": [],
                      "estimatedHours": 6.0
                    },
                    {
                      "weekNumber": 3,
                      "topic": "프로젝트 캐시 설계",
                      "reason": "포트폴리오에 남길 캐시 적용 근거가 필요함",
                      "tasks": [
                        {
                          "type": "APPLY_PROJECT",
                          "title": "기존 프로젝트에 조회 캐시 적용하기"
                        }
                      ],
                      "materials": [],
                      "estimatedHours": 5.0
                    },
                    {
                      "weekNumber": 4,
                      "topic": "학습 회고",
                      "reason": "학습 결과를 정리하고 다음 보완점을 확인해야 함",
                      "tasks": [
                        {
                          "type": "WRITE_NOTE",
                          "title": "캐시 학습 회고 작성"
                        }
                      ],
                      "materials": [],
                      "estimatedHours": 3.0
                    }
                  ]
                }
                """;
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
