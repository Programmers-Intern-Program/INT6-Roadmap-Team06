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
import com.back.coach.global.security.JwtTokenProvider;
import com.back.coach.support.ApiTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultMatcher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class V1ApiOwnershipIntegrationTest extends ApiTestBase {

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
    private ProgressLogRepository progressLogRepository;

    @Autowired
    private RoadmapWeekRepository roadmapWeekRepository;

    @Autowired
    private JobRoleRepository jobRoleRepository;

    @Autowired
    private GithubAnalysisPayloadJson githubAnalysisPayloadJson;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("GitHub 분석 상세 조회 API는 다른 사용자 접근을 차단한다")
    void githubAnalysisDetailApi_rejectsOtherUserAccess() throws Exception {
        User owner = saveUser("github-owner");
        User other = saveUser("github-other");
        OwnershipFixture fixture = saveOwnershipFixture(owner.getId(), 1);
        Long githubAnalysisId = fixture.githubAnalysis().getId();

        mockMvc.perform(get("/api/github-analyses/{githubAnalysisId}", githubAnalysisId)
                        .cookie(accessTokenCookie(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.githubAnalysisId").value(String.valueOf(githubAnalysisId)));

        mockMvc.perform(get("/api/github-analyses/{githubAnalysisId}", githubAnalysisId)
                        .cookie(accessTokenCookie(other)))
                .andExpect(status().isNotFound())
                .andExpect(resourceNotFoundCode())
                .andExpect(nonEmptyMessage());
    }

    @Test
    @DisplayName("진단 상세 조회 API는 다른 사용자 접근을 차단한다")
    void diagnosisDetailApi_rejectsOtherUserAccess() throws Exception {
        User owner = saveUser("diagnosis-owner");
        User other = saveUser("diagnosis-other");
        OwnershipFixture fixture = saveOwnershipFixture(owner.getId(), 1);
        Long diagnosisId = fixture.diagnosis().getId();

        mockMvc.perform(get("/api/diagnoses/{diagnosisId}", diagnosisId)
                        .cookie(accessTokenCookie(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.diagnosisId").value(String.valueOf(diagnosisId)));

        mockMvc.perform(get("/api/diagnoses/{diagnosisId}", diagnosisId)
                        .cookie(accessTokenCookie(other)))
                .andExpect(status().isNotFound())
                .andExpect(resourceNotFoundCode())
                .andExpect(nonEmptyMessage());
    }

    @Test
    @DisplayName("로드맵 상세 조회 API는 다른 사용자 접근을 차단한다")
    void roadmapDetailApi_rejectsOtherUserAccess() throws Exception {
        User owner = saveUser("roadmap-owner");
        User other = saveUser("roadmap-other");
        OwnershipFixture fixture = saveOwnershipFixture(owner.getId(), 1);
        Long roadmapId = fixture.roadmap().getId();

        mockMvc.perform(get("/api/roadmaps/{roadmapId}", roadmapId)
                        .cookie(accessTokenCookie(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roadmapId").value(String.valueOf(roadmapId)));

        mockMvc.perform(get("/api/roadmaps/{roadmapId}", roadmapId)
                        .cookie(accessTokenCookie(other)))
                .andExpect(status().isNotFound())
                .andExpect(resourceNotFoundCode())
                .andExpect(nonEmptyMessage());
    }

    @Test
    @DisplayName("진도 저장 API는 다른 사용자 로드맵 접근을 차단하고 로그를 저장하지 않는다")
    void progressApi_rejectsOtherUserRoadmapAccessWithoutSavingLog() throws Exception {
        User owner = saveUser("progress-owner");
        User other = saveUser("progress-other");
        OwnershipFixture fixture = saveOwnershipFixture(owner.getId(), 1);
        RoadmapWeek week = fixture.weeks().getFirst();
        long progressLogCount = progressLogRepository.count();

        mockMvc.perform(post("/api/roadmaps/{roadmapId}/progress", fixture.roadmap().getId())
                        .cookie(accessTokenCookie(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(progressRequestBody(week.getId(), "DONE", "다른 사용자 진도 저장")))
                .andExpect(status().isNotFound())
                .andExpect(resourceNotFoundCode())
                .andExpect(nonEmptyMessage());

        assertThat(progressLogRepository.count()).isEqualTo(progressLogCount);
        assertThat(progressLogRepository.findByUserIdAndRoadmapWeekIdOrderByCreatedAtDesc(other.getId(), week.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("진도 저장 API는 다른 사용자 주차 접근을 차단하고 로그를 저장하지 않는다")
    void progressApi_rejectsOtherUserRoadmapWeekWithoutSavingLog() throws Exception {
        User owner = saveUser("week-owner");
        User other = saveUser("week-other");
        OwnershipFixture ownerFixture = saveOwnershipFixture(owner.getId(), 1);
        OwnershipFixture otherFixture = saveOwnershipFixture(other.getId(), 1);
        RoadmapWeek otherWeek = otherFixture.weeks().getFirst();
        long progressLogCount = progressLogRepository.count();

        mockMvc.perform(post("/api/roadmaps/{roadmapId}/progress", ownerFixture.roadmap().getId())
                        .cookie(accessTokenCookie(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(progressRequestBody(otherWeek.getId(), "DONE", "다른 사용자 주차 진도 저장")))
                .andExpect(status().isNotFound())
                .andExpect(resourceNotFoundCode())
                .andExpect(nonEmptyMessage());

        assertThat(progressLogRepository.count()).isEqualTo(progressLogCount);
        assertThat(progressLogRepository.findByUserIdAndRoadmapWeekIdOrderByCreatedAtDesc(owner.getId(), otherWeek.getId()))
                .isEmpty();
    }

    private OwnershipFixture saveOwnershipFixture(Long userId, int totalWeeks) {
        UserProfile profile = saveProfileFixture(userId);
        GithubAnalysis githubAnalysis = saveGithubAnalysisFixture(userId);
        JobRole jobRole = backendDeveloperRole();
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
        List<RoadmapWeek> weeks = roadmapWeekRepository.saveAll(roadmapWeeks(roadmap.getId(), totalWeeks));
        return new OwnershipFixture(githubAnalysis, diagnosis, roadmap, weeks);
    }

    private UserProfile saveProfileFixture(Long userId) {
        JobRole jobRole = backendDeveloperRole();
        return userProfileRepository.save(UserProfile.create(
                userId,
                jobRole.getId(),
                CurrentLevel.JUNIOR,
                10,
                LocalDate.of(2099, 12, 31),
                "[\"Backend\"]",
                null,
                null
        ));
    }

    private GithubAnalysis saveGithubAnalysisFixture(Long userId) {
        GithubConnection connection = githubConnectionRepository.save(GithubConnection.connect(
                userId,
                unique("gh-user"),
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
                  "recommendations": ["Redis 캐시와 TTL 기반 설계를 먼저 학습"]
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

    private JobRole backendDeveloperRole() {
        return jobRoleRepository.findByRoleCodeAndActiveTrue("BACKEND_DEVELOPER")
                .orElseThrow();
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

    private String progressRequestBody(Long roadmapWeekId, String status, String note) {
        return """
                {
                  "roadmapWeekId": %d,
                  "status": "%s",
                  "note": "%s"
                }
                """.formatted(roadmapWeekId, status, note);
    }

    private ResultMatcher resourceNotFoundCode() {
        return jsonPath("$.code").value("RESOURCE_NOT_FOUND");
    }

    private ResultMatcher nonEmptyMessage() {
        return jsonPath("$.message").isNotEmpty();
    }

    private record OwnershipFixture(
            GithubAnalysis githubAnalysis,
            CapabilityDiagnosis diagnosis,
            LearningRoadmap roadmap,
            List<RoadmapWeek> weeks
    ) {
    }
}
