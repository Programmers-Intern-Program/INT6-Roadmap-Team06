package com.back.coach.domain.flow;

import com.back.coach.domain.diagnosis.entity.CapabilityDiagnosis;
import com.back.coach.domain.diagnosis.repository.CapabilityDiagnosisRepository;
import com.back.coach.domain.github.dto.GithubAnalysisPayload;
import com.back.coach.domain.github.entity.GithubAnalysis;
import com.back.coach.domain.github.entity.GithubConnection;
import com.back.coach.domain.github.repository.GithubAnalysisRepository;
import com.back.coach.domain.github.repository.GithubConnectionRepository;
import com.back.coach.domain.github.service.GithubAnalysisPayloadJson;
import com.back.coach.domain.jobrole.entity.JobRole;
import com.back.coach.domain.jobrole.repository.JobRoleRepository;
import com.back.coach.domain.roadmap.entity.LearningRoadmap;
import com.back.coach.domain.roadmap.entity.ProgressLog;
import com.back.coach.domain.roadmap.entity.RoadmapWeek;
import com.back.coach.domain.roadmap.repository.LearningRoadmapRepository;
import com.back.coach.domain.roadmap.repository.ProgressLogRepository;
import com.back.coach.domain.roadmap.repository.RoadmapWeekRepository;
import com.back.coach.domain.user.entity.User;
import com.back.coach.domain.user.entity.UserProfile;
import com.back.coach.domain.user.repository.UserProfileRepository;
import com.back.coach.domain.user.repository.UserRepository;
import com.back.coach.global.code.AuthProvider;
import com.back.coach.global.code.CurrentLevel;
import com.back.coach.global.code.GithubAccessType;
import com.back.coach.global.code.ProgressStatus;
import com.back.coach.global.security.JwtTokenProvider;
import com.back.coach.support.ApiTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class V1ApiStorageRequeryIntegrationTest extends ApiTestBase {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

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
    private JobRoleRepository jobRoleRepository;

    @Autowired
    private GithubAnalysisPayloadJson githubAnalysisPayloadJson;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("진도 저장 후 로드맵 상세와 대시보드 API가 최신 progress를 반환한다")
    void progressApi_persistsAppendOnlyLogsAndRequeriesLatestState() throws Exception {
        User user = saveUser("api-flow");
        Cookie accessToken = accessTokenCookie(user);
        LearningRoadmap roadmap = saveRoadmapFixture(user.getId(), 2);
        List<RoadmapWeek> weeks = roadmapWeekRepository.findByRoadmapIdOrderByWeekNumberAsc(roadmap.getId());
        Long firstWeekId = weeks.getFirst().getId();

        mockMvc.perform(post("/api/roadmaps/{roadmapId}/progress", roadmap.getId())
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "roadmapWeekId", firstWeekId,
                                "status", "IN_PROGRESS",
                                "note", "Redis 공식 문서 읽기 시작"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roadmapWeekId").value(String.valueOf(firstWeekId)))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.savedAt").isNotEmpty());

        Thread.sleep(20);

        mockMvc.perform(post("/api/roadmaps/{roadmapId}/progress", roadmap.getId())
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "roadmapWeekId", firstWeekId,
                                "status", "DONE",
                                "note", "Redis 공식 문서 정리 완료"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roadmapWeekId").value(String.valueOf(firstWeekId)))
                .andExpect(jsonPath("$.data.status").value("DONE"));

        List<ProgressLog> progressLogs = progressLogRepository
                .findByUserIdAndRoadmapWeekIdOrderByCreatedAtDesc(user.getId(), firstWeekId);
        assertThat(progressLogs).hasSize(2);
        assertThat(progressLogs)
                .extracting(ProgressLog::getStatus)
                .containsExactly(ProgressStatus.DONE, ProgressStatus.IN_PROGRESS);

        mockMvc.perform(get("/api/roadmaps/{roadmapId}", roadmap.getId())
                        .cookie(accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roadmapId").value(String.valueOf(roadmap.getId())))
                .andExpect(jsonPath("$.data.weeks[0].roadmapWeekId").value(String.valueOf(firstWeekId)))
                .andExpect(jsonPath("$.data.weeks[0].progressStatus").value("DONE"))
                .andExpect(jsonPath("$.data.weeks[0].progressNote").value("Redis 공식 문서 정리 완료"))
                .andExpect(jsonPath("$.data.weeks[0].progressUpdatedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.weeks[1].progressStatus").value("TODO"));

        mockMvc.perform(get("/api/dashboard")
                        .cookie(accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roadmap.roadmapId").value(String.valueOf(roadmap.getId())))
                .andExpect(jsonPath("$.data.roadmap.progress.totalWeeks").value(2))
                .andExpect(jsonPath("$.data.roadmap.progress.todoWeeks").value(1))
                .andExpect(jsonPath("$.data.roadmap.progress.inProgressWeeks").value(0))
                .andExpect(jsonPath("$.data.roadmap.progress.doneWeeks").value(1))
                .andExpect(jsonPath("$.data.roadmap.progress.skippedWeeks").value(0));
    }

    private LearningRoadmap saveRoadmapFixture(Long userId, int totalWeeks) {
        JobRole jobRole = jobRoleRepository.findByRoleCodeAndActiveTrue("BACKEND_DEVELOPER")
                .orElseThrow();
        UserProfile profile = userProfileRepository.save(UserProfile.create(
                userId,
                jobRole.getId(),
                CurrentLevel.JUNIOR,
                10,
                LocalDate.of(2099, 12, 31),
                "[\"Backend\"]",
                null,
                null
        ));
        GithubConnection connection = githubConnectionRepository.save(GithubConnection.connect(
                userId,
                unique("gh-user"),
                unique("login"),
                GithubAccessType.OAUTH,
                "ghp_test_token"
        ));
        GithubAnalysis githubAnalysis = githubAnalysisRepository.save(GithubAnalysis.create(
                userId,
                connection.getId(),
                1,
                "Spring Boot 중심 GitHub 분석",
                githubAnalysisPayloadJson.toJson(githubAnalysisPayload())
        ));
        CapabilityDiagnosis diagnosis = capabilityDiagnosisRepository.save(CapabilityDiagnosis.create(
                userId,
                profile.getId(),
                githubAnalysis.getId(),
                jobRole.getId(),
                1,
                CurrentLevel.JUNIOR,
                "Redis 보완 필요",
                diagnosisPayload()
        ));
        LearningRoadmap roadmap = learningRoadmapRepository.save(LearningRoadmap.create(
                userId,
                diagnosis.getId(),
                1,
                totalWeeks,
                "Redis 중심 로드맵",
                roadmapPayload(totalWeeks)
        ));
        roadmapWeekRepository.saveAll(roadmapWeeks(roadmap.getId(), totalWeeks));
        return roadmap;
    }

    private List<RoadmapWeek> roadmapWeeks(Long roadmapId, int totalWeeks) {
        return java.util.stream.IntStream.rangeClosed(1, totalWeeks)
                .mapToObj(weekNumber -> RoadmapWeek.create(
                        roadmapId,
                        weekNumber,
                        "Redis " + weekNumber + "주차",
                        "캐시 설계 경험 보완",
                        "[{\"type\":\"READ_DOCS\",\"title\":\"Redis 공식 문서 읽기\"}]",
                        "[{\"type\":\"DOCS\",\"title\":\"Redis Documentation\",\"url\":\"https://redis.io/docs\"}]",
                        new BigDecimal("8.0")
                ))
                .toList();
    }

    private GithubAnalysisPayload githubAnalysisPayload() {
        return new GithubAnalysisPayload(
                new GithubAnalysisPayload.StaticSignals(
                        List.of(new GithubAnalysisPayload.PrimaryLanguage("Java", 1.0)),
                        1,
                        "WEEKLY",
                        "CONSISTENT"
                ),
                List.of(),
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

    private String diagnosisPayload() {
        return """
                {
                  "missingSkills": [
                    {
                      "skillName": "Redis",
                      "severity": "HIGH",
                      "reason": "캐시 설계 경험이 부족함",
                      "priorityOrder": 1
                    }
                  ],
                  "strengths": ["Spring Boot"],
                  "recommendations": ["Redis 캐시와 TTL 기반 설계를 먼저 학습"],
                  "githubInsights": {
                    "confirmedSkills": ["Spring Boot"],
                    "newFromGithub": []
                  }
                }
                """;
    }

    private String roadmapPayload(int totalWeeks) {
        String weeksJson = java.util.stream.IntStream.rangeClosed(1, totalWeeks)
                .mapToObj(weekNumber -> """
                        {
                          "weekNumber": %d,
                          "topic": "Redis %d주차",
                          "reason": "캐시 설계 경험 보완",
                          "tasks": [{"type": "READ_DOCS", "title": "Redis 공식 문서 읽기"}],
                          "materials": [{"type": "DOCS", "title": "Redis Documentation", "url": "https://redis.io/docs"}],
                          "estimatedHours": 8.0
                        }
                        """.formatted(weekNumber, weekNumber))
                .collect(java.util.stream.Collectors.joining(","));
        return "{\"weeks\":[" + weeksJson + "]}";
    }

    private Cookie accessTokenCookie(User user) {
        return new Cookie("accessToken", jwtTokenProvider.createAccessToken(user.getId()));
    }

    private User saveUser(String prefix) {
        return userRepository.save(User.signupFromOAuth(
                AuthProvider.GITHUB,
                unique(prefix + "-gh"),
                unique(prefix) + "@example.com"
        ));
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
