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
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class V1LatestDetailContractIntegrationTest extends ApiTestBase {

    private static final Instant LOWER_VERSION_NEWER_CREATED_AT = Instant.parse("2026-05-01T00:00:00Z");
    private static final Instant HIGHER_VERSION_OLDER_CREATED_AT = Instant.parse("2026-04-01T00:00:00Z");

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
    private JobRoleRepository jobRoleRepository;

    @Autowired
    private GithubAnalysisPayloadJson githubAnalysisPayloadJson;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("대시보드 API는 created_at보다 version이 높은 결과를 latest로 선택한다")
    void dashboardApi_selectsLatestResultsByVersionBeforeCreatedAt() throws Exception {
        User user = saveUser("latest-user");
        VersionedResultFixture fixture = saveVersionedResultFixture(user.getId());

        mockMvc.perform(get("/api/dashboard")
                        .cookie(accessTokenCookie(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.githubAnalysis.githubAnalysisId")
                        .value(String.valueOf(fixture.latestGithubAnalysis().getId())))
                .andExpect(jsonPath("$.data.githubAnalysis.version").value(2))
                .andExpect(jsonPath("$.data.githubAnalysis.summary").value("latest GitHub analysis"))
                .andExpect(jsonPath("$.data.githubAnalysis.finalTechProfile.confirmedSkills[0]")
                        .value("Spring Boot v2"))
                .andExpect(jsonPath("$.data.diagnosis.diagnosisId")
                        .value(String.valueOf(fixture.latestDiagnosis().getId())))
                .andExpect(jsonPath("$.data.diagnosis.version").value(2))
                .andExpect(jsonPath("$.data.diagnosis.summary").value("latest diagnosis"))
                .andExpect(jsonPath("$.data.roadmap.roadmapId")
                        .value(String.valueOf(fixture.latestRoadmap().getId())))
                .andExpect(jsonPath("$.data.roadmap.version").value(2))
                .andExpect(jsonPath("$.data.roadmap.summary").value("latest roadmap"))
                .andExpect(jsonPath("$.data.roadmap.progress.totalWeeks").value(2))
                .andExpect(jsonPath("$.data.roadmap.progress.todoWeeks").value(2));
    }

    private VersionedResultFixture saveVersionedResultFixture(Long userId) {
        UserProfile profile = saveProfileFixture(userId);
        GithubConnection connection = saveGithubConnectionFixture(userId);
        JobRole jobRole = backendDeveloperRole();

        GithubAnalysis oldGithubAnalysis = saveGithubAnalysisFixture(
                userId,
                connection.getId(),
                1,
                "old GitHub analysis",
                "Spring Boot v1"
        );
        GithubAnalysis latestGithubAnalysis = saveGithubAnalysisFixture(
                userId,
                connection.getId(),
                2,
                "latest GitHub analysis",
                "Spring Boot v2"
        );

        CapabilityDiagnosis oldDiagnosis = saveDiagnosisFixture(
                userId,
                profile.getId(),
                oldGithubAnalysis.getId(),
                jobRole.getId(),
                1,
                "old diagnosis"
        );
        CapabilityDiagnosis latestDiagnosis = saveDiagnosisFixture(
                userId,
                profile.getId(),
                latestGithubAnalysis.getId(),
                jobRole.getId(),
                2,
                "latest diagnosis"
        );

        LearningRoadmap oldRoadmap = saveRoadmapFixture(userId, oldDiagnosis.getId(), 1, 1, "old roadmap");
        LearningRoadmap latestRoadmap = saveRoadmapFixture(userId, latestDiagnosis.getId(), 2, 2, "latest roadmap");

        updateCreatedAt("github_analyses", oldGithubAnalysis.getId(), LOWER_VERSION_NEWER_CREATED_AT);
        updateCreatedAt("github_analyses", latestGithubAnalysis.getId(), HIGHER_VERSION_OLDER_CREATED_AT);
        updateCreatedAt("capability_diagnoses", oldDiagnosis.getId(), LOWER_VERSION_NEWER_CREATED_AT);
        updateCreatedAt("capability_diagnoses", latestDiagnosis.getId(), HIGHER_VERSION_OLDER_CREATED_AT);
        updateCreatedAt("learning_roadmaps", oldRoadmap.getId(), LOWER_VERSION_NEWER_CREATED_AT);
        updateCreatedAt("learning_roadmaps", latestRoadmap.getId(), HIGHER_VERSION_OLDER_CREATED_AT);

        return new VersionedResultFixture(
                oldGithubAnalysis,
                latestGithubAnalysis,
                oldDiagnosis,
                latestDiagnosis,
                oldRoadmap,
                latestRoadmap
        );
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

    private GithubConnection saveGithubConnectionFixture(Long userId) {
        return githubConnectionRepository.save(GithubConnection.connect(
                userId,
                unique("gh-user"),
                unique("login"),
                GithubAccessType.OAUTH,
                "ghp_test_token"
        ));
    }

    private GithubAnalysis saveGithubAnalysisFixture(
            Long userId,
            Long connectionId,
            int version,
            String summary,
            String confirmedSkill
    ) {
        return githubAnalysisRepository.save(GithubAnalysis.create(
                userId,
                connectionId,
                version,
                summary,
                githubAnalysisPayloadJson.toJson(githubAnalysisPayload(confirmedSkill))
        ));
    }

    private CapabilityDiagnosis saveDiagnosisFixture(
            Long userId,
            Long profileId,
            Long githubAnalysisId,
            Long jobRoleId,
            int version,
            String summary
    ) {
        return capabilityDiagnosisRepository.save(CapabilityDiagnosis.create(
                userId,
                profileId,
                githubAnalysisId,
                jobRoleId,
                version,
                CurrentLevel.JUNIOR,
                summary,
                diagnosisPayload()
        ));
    }

    private LearningRoadmap saveRoadmapFixture(
            Long userId,
            Long diagnosisId,
            int version,
            int totalWeeks,
            String summary
    ) {
        LearningRoadmap roadmap = learningRoadmapRepository.save(LearningRoadmap.create(
                userId,
                diagnosisId,
                version,
                totalWeeks,
                summary,
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

    private GithubAnalysisPayload githubAnalysisPayload(String confirmedSkill) {
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
                        List.of(confirmedSkill),
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

    private void updateCreatedAt(String tableName, Long id, Instant createdAt) {
        jdbcTemplate.update(
                "UPDATE " + tableName + " SET created_at = ? WHERE id = ?",
                Timestamp.from(createdAt),
                id
        );
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

    private record VersionedResultFixture(
            GithubAnalysis oldGithubAnalysis,
            GithubAnalysis latestGithubAnalysis,
            CapabilityDiagnosis oldDiagnosis,
            CapabilityDiagnosis latestDiagnosis,
            LearningRoadmap oldRoadmap,
            LearningRoadmap latestRoadmap
    ) {
    }
}
