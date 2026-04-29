package com.back.coach.domain.result.repository;

import com.back.coach.domain.diagnosis.entity.CapabilityDiagnosis;
import com.back.coach.domain.diagnosis.repository.CapabilityDiagnosisRepository;
import com.back.coach.domain.github.entity.GithubAnalysis;
import com.back.coach.domain.github.repository.GithubAnalysisRepository;
import com.back.coach.domain.roadmap.entity.LearningRoadmap;
import com.back.coach.domain.roadmap.repository.LearningRoadmapRepository;
import com.back.coach.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class ResultLatestVersionRepositoryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GithubAnalysisRepository githubAnalysisRepository;

    @Autowired
    private CapabilityDiagnosisRepository capabilityDiagnosisRepository;

    @Autowired
    private LearningRoadmapRepository learningRoadmapRepository;

    @Test
    @DisplayName("결과 latest 조회는 created_at보다 version을 우선한다")
    void latestResultQueriesPreferVersionBeforeCreatedAt() {
        Long userId = insertUser();
        Long githubConnectionId = insertGithubConnection(userId);
        Long jobRoleId = findBackendDeveloperRoleId();

        Long githubAnalysisV1 = insertGithubAnalysis(userId, githubConnectionId, 1, "2026-01-03T00:00:00Z");
        Long githubAnalysisV2 = insertGithubAnalysis(userId, githubConnectionId, 2, "2026-01-01T00:00:00Z");
        Long profileId = insertUserProfile(userId, jobRoleId);

        Long diagnosisV1 = insertCapabilityDiagnosis(
                userId, profileId, githubAnalysisV2, jobRoleId, 1, "2026-01-03T00:10:00Z");
        Long diagnosisV2 = insertCapabilityDiagnosis(
                userId, profileId, githubAnalysisV2, jobRoleId, 2, "2026-01-01T00:10:00Z");

        Long roadmapV1 = insertLearningRoadmap(userId, diagnosisV2, 1, "2026-01-03T00:20:00Z");
        Long roadmapV2 = insertLearningRoadmap(userId, diagnosisV2, 2, "2026-01-01T00:20:00Z");

        GithubAnalysis latestGithubAnalysis = githubAnalysisRepository
                .findTopByUserIdOrderByVersionDescCreatedAtDesc(userId)
                .orElseThrow();
        CapabilityDiagnosis latestDiagnosis = capabilityDiagnosisRepository
                .findTopByUserIdOrderByVersionDescCreatedAtDesc(userId)
                .orElseThrow();
        LearningRoadmap latestRoadmap = learningRoadmapRepository
                .findTopByUserIdOrderByVersionDescCreatedAtDesc(userId)
                .orElseThrow();

        assertThat(latestGithubAnalysis.getId()).isEqualTo(githubAnalysisV2);
        assertThat(latestDiagnosis.getId()).isEqualTo(diagnosisV2);
        assertThat(latestRoadmap.getId()).isEqualTo(roadmapV2);
        assertThat(List.of(githubAnalysisV1, diagnosisV1, roadmapV1)).doesNotContainNull();

        assertThat(githubAnalysisRepository.findMaxVersionByUserId(userId)).isEqualTo(2);
        assertThat(capabilityDiagnosisRepository.findMaxVersionByUserId(userId)).isEqualTo(2);
        assertThat(learningRoadmapRepository.findMaxVersionByUserId(userId)).isEqualTo(2);
    }

    @Test
    @DisplayName("결과 latest 조회용 version 인덱스가 생성된다")
    void latestVersionIndexesExist() {
        List<String> indexNames = jdbcTemplate.queryForList("""
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = current_schema()
                """, String.class);

        assertThat(indexNames)
                .contains(
                        "idx_github_analyses_user_version_created_at",
                        "idx_capability_diagnoses_user_version_created_at",
                        "idx_learning_roadmaps_user_version_created_at"
                );
    }

    private Long insertUser() {
        String email = "latest-version-" + UUID.randomUUID() + "@example.com";
        return jdbcTemplate.queryForObject("""
                INSERT INTO users (email, password_hash, auth_provider, is_active)
                VALUES (?, 'hash', 'LOCAL', true)
                RETURNING id
                """, Long.class, email);
    }

    private Long insertGithubConnection(Long userId) {
        String githubUserId = "github-" + UUID.randomUUID();
        return jdbcTemplate.queryForObject("""
                INSERT INTO github_connections (user_id, github_user_id, github_login, access_type)
                VALUES (?, ?, 'latest-version-user', 'OAUTH')
                RETURNING id
                """, Long.class, userId, githubUserId);
    }

    private Long findBackendDeveloperRoleId() {
        return jdbcTemplate.queryForObject("""
                SELECT id
                FROM job_roles
                WHERE role_code = 'BACKEND_DEVELOPER'
                """, Long.class);
    }

    private Long insertUserProfile(Long userId, Long jobRoleId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO user_profiles (user_id, job_role_id, current_level, interest_areas_json)
                VALUES (?, ?, 'JUNIOR', '[]'::jsonb)
                RETURNING id
                """, Long.class, userId, jobRoleId);
    }

    private Long insertGithubAnalysis(Long userId, Long githubConnectionId, int version, String createdAt) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO github_analyses (
                    user_id, github_connection_id, version, summary, analysis_payload, created_at
                )
                VALUES (?, ?, ?, ?, '{}'::jsonb, ?::timestamptz)
                RETURNING id
                """, Long.class, userId, githubConnectionId, version, "GitHub analysis v" + version, createdAt);
    }

    private Long insertCapabilityDiagnosis(
            Long userId,
            Long profileId,
            Long githubAnalysisId,
            Long jobRoleId,
            int version,
            String createdAt
    ) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO capability_diagnoses (
                    user_id,
                    profile_id,
                    github_analysis_id,
                    job_role_id,
                    version,
                    current_level,
                    summary,
                    diagnosis_payload,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, 'JUNIOR', ?, '{}'::jsonb, ?::timestamptz)
                RETURNING id
                """, Long.class, userId, profileId, githubAnalysisId, jobRoleId, version,
                "Capability diagnosis v" + version, createdAt);
    }

    private Long insertLearningRoadmap(Long userId, Long diagnosisId, int version, String createdAt) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO learning_roadmaps (
                    user_id, diagnosis_id, version, total_weeks, summary, roadmap_payload, created_at
                )
                VALUES (?, ?, ?, 4, ?, '{}'::jsonb, ?::timestamptz)
                RETURNING id
                """, Long.class, userId, diagnosisId, version, "Learning roadmap v" + version, createdAt);
    }
}
