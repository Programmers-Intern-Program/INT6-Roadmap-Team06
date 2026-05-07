package com.back.coach.domain.pattern.service;

import com.back.coach.domain.pattern.entity.DetectedPattern;
import com.back.coach.global.code.PatternType;
import com.back.coach.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class ProgressPatternDetectorServiceIntegrationTest {

    @Autowired
    private ProgressPatternDetectorService progressPatternDetectorService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("최근 7일 같은 주차의 미완료 로그 누적으로 반복 미완료 패턴을 저장한다")
    void detectsRepeatedIncompletePatternFromRecentProgressLogs() throws Exception {
        Long userId = insertUser();
        Long roadmapId = insertLearningRoadmapGraph(userId);
        Long roadmapWeekId = insertRoadmapWeek(roadmapId, 2);
        insertProgressLog(userId, roadmapWeekId, "IN_PROGRESS", Instant.now().minus(2, ChronoUnit.DAYS));
        insertProgressLog(userId, roadmapWeekId, "SKIPPED", Instant.now().minus(1, ChronoUnit.DAYS));

        List<DetectedPattern> detectedPatterns = progressPatternDetectorService.detectAndStore(userId);

        assertThat(detectedPatterns)
                .extracting(DetectedPattern::getPatternType)
                .contains(PatternType.REPEATED_INCOMPLETE);
        Long patternId = findDetectedPatternId(userId, "REPEATED_INCOMPLETE");
        JsonNode metadata = readMetadata(patternId);
        assertThat(metadata.path("targetType").asText()).isEqualTo("roadmap_week");
        assertThat(metadata.path("targetId").asLong()).isEqualTo(roadmapWeekId);
        assertThat(metadata.path("windowDays").asInt()).isEqualTo(7);
        assertThat(metadata.path("count").asLong()).isEqualTo(2L);

        progressPatternDetectorService.detectAndStore(userId);

        assertThat(countDetectedPatterns(userId, "REPEATED_INCOMPLETE")).isEqualTo(1L);
    }

    @Test
    @DisplayName("인접 주차 최신 상태가 미완료이면 연속 지연 패턴을 저장한다")
    void detectsConsecutiveDelayPatternFromAdjacentLatestStatuses() throws Exception {
        Long userId = insertUser();
        Long roadmapId = insertLearningRoadmapGraph(userId);
        Long firstWeekId = insertRoadmapWeek(roadmapId, 1);
        Long secondWeekId = insertRoadmapWeek(roadmapId, 2);
        insertRoadmapWeek(roadmapId, 3);
        insertProgressLog(userId, firstWeekId, "IN_PROGRESS", Instant.now().minus(2, ChronoUnit.DAYS));
        insertProgressLog(userId, secondWeekId, "SKIPPED", Instant.now().minus(1, ChronoUnit.DAYS));

        List<DetectedPattern> detectedPatterns = progressPatternDetectorService.detectAndStore(userId);

        assertThat(detectedPatterns)
                .extracting(DetectedPattern::getPatternType)
                .contains(PatternType.CONSECUTIVE_DELAY);
        Long patternId = findDetectedPatternId(userId, "CONSECUTIVE_DELAY");
        JsonNode metadata = readMetadata(patternId);
        assertThat(metadata.path("targetType").asText()).isEqualTo("roadmap");
        assertThat(metadata.path("targetId").asLong()).isEqualTo(roadmapId);
        assertThat(metadata.path("weekNumbers")).extracting(JsonNode::asInt).containsExactly(1, 2);
        assertThat(metadata.path("roadmapWeekIds")).extracting(JsonNode::asLong)
                .containsExactly(firstWeekId, secondWeekId);
    }

    @Test
    @DisplayName("최신 상태가 DONE이거나 다른 사용자 데이터이면 감지하지 않는다")
    void ignoresDoneLatestStatusAndOtherUserData() {
        Long userId = insertUser();
        Long roadmapId = insertLearningRoadmapGraph(userId);
        Long roadmapWeekId = insertRoadmapWeek(roadmapId, 1);
        insertProgressLog(userId, roadmapWeekId, "IN_PROGRESS", Instant.now().minus(3, ChronoUnit.DAYS));
        insertProgressLog(userId, roadmapWeekId, "SKIPPED", Instant.now().minus(2, ChronoUnit.DAYS));
        insertProgressLog(userId, roadmapWeekId, "DONE", Instant.now().minus(1, ChronoUnit.DAYS));

        Long otherUserId = insertUser();
        Long otherRoadmapId = insertLearningRoadmapGraph(otherUserId);
        Long otherRoadmapWeekId = insertRoadmapWeek(otherRoadmapId, 1);
        insertProgressLog(otherUserId, otherRoadmapWeekId, "IN_PROGRESS", Instant.now().minus(2, ChronoUnit.DAYS));
        insertProgressLog(otherUserId, otherRoadmapWeekId, "SKIPPED", Instant.now().minus(1, ChronoUnit.DAYS));

        List<DetectedPattern> detectedPatterns = progressPatternDetectorService.detectAndStore(userId);

        assertThat(detectedPatterns).isEmpty();
        assertThat(countDetectedPatterns(userId, "REPEATED_INCOMPLETE")).isZero();
        assertThat(countDetectedPatterns(userId, "CONSECUTIVE_DELAY")).isZero();
    }

    private Long insertUser() {
        String key = UUID.randomUUID().toString();
        return jdbcTemplate.queryForObject("""
                INSERT INTO users (email, password_hash, auth_provider, is_active)
                VALUES (?, 'hash', 'LOCAL', true)
                RETURNING id
                """, Long.class, "progress-pattern-" + key + "@example.com");
    }

    private Long insertLearningRoadmapGraph(Long userId) {
        Long githubConnectionId = insertGithubConnection(userId);
        Long jobRoleId = findBackendDeveloperRoleId();
        Long profileId = insertUserProfile(userId, jobRoleId);
        Long githubAnalysisId = insertGithubAnalysis(userId, githubConnectionId);
        Long diagnosisId = insertCapabilityDiagnosis(userId, profileId, githubAnalysisId, jobRoleId);
        return insertLearningRoadmap(userId, diagnosisId);
    }

    private Long insertGithubConnection(Long userId) {
        String githubUserId = "progress-pattern-" + UUID.randomUUID();
        return jdbcTemplate.queryForObject("""
                INSERT INTO github_connections (user_id, github_user_id, github_login, access_type)
                VALUES (?, ?, 'progress-pattern-user', 'OAUTH')
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

    private Long insertGithubAnalysis(Long userId, Long githubConnectionId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO github_analyses (
                    user_id, github_connection_id, version, summary, analysis_payload
                )
                VALUES (?, ?, 1, 'GitHub analysis', '{}'::jsonb)
                RETURNING id
                """, Long.class, userId, githubConnectionId);
    }

    private Long insertCapabilityDiagnosis(Long userId, Long profileId, Long githubAnalysisId, Long jobRoleId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO capability_diagnoses (
                    user_id,
                    profile_id,
                    github_analysis_id,
                    job_role_id,
                    version,
                    current_level,
                    summary,
                    diagnosis_payload
                )
                VALUES (?, ?, ?, ?, 1, 'JUNIOR', 'Capability diagnosis', '{}'::jsonb)
                RETURNING id
                """, Long.class, userId, profileId, githubAnalysisId, jobRoleId);
    }

    private Long insertLearningRoadmap(Long userId, Long diagnosisId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO learning_roadmaps (
                    user_id, diagnosis_id, version, total_weeks, summary, roadmap_payload
                )
                VALUES (?, ?, 1, 4, 'Learning roadmap', '{}'::jsonb)
                RETURNING id
                """, Long.class, userId, diagnosisId);
    }

    private Long insertRoadmapWeek(Long roadmapId, int weekNumber) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO roadmap_weeks (
                    roadmap_id, week_number, topic, reason_text, tasks_json, materials_json, estimated_hours
                )
                VALUES (?, ?, ?, 'Reason', '[]'::jsonb, '[]'::jsonb, 4.0)
                RETURNING id
                """, Long.class, roadmapId, weekNumber, "Week " + weekNumber);
    }

    private Long insertProgressLog(Long userId, Long roadmapWeekId, String status, Instant createdAt) {
        Timestamp completedAt = "DONE".equals(status) ? Timestamp.from(createdAt) : null;
        return jdbcTemplate.queryForObject("""
                INSERT INTO progress_logs (user_id, roadmap_week_id, status, note, completed_at, created_at)
                VALUES (?, ?, ?, null, ?, ?)
                RETURNING id
                """, Long.class, userId, roadmapWeekId, status, completedAt, Timestamp.from(createdAt));
    }

    private Long findDetectedPatternId(Long userId, String patternType) {
        return jdbcTemplate.queryForObject("""
                SELECT id
                FROM detected_patterns
                WHERE user_id = ?
                  AND pattern_type = ?
                ORDER BY created_at DESC
                LIMIT 1
                """, Long.class, userId, patternType);
    }

    private JsonNode readMetadata(Long patternId) throws Exception {
        String metadata = jdbcTemplate.queryForObject("""
                SELECT metadata::text
                FROM detected_patterns
                WHERE id = ?
                """, String.class, patternId);
        return objectMapper.readTree(metadata);
    }

    private Long countDetectedPatterns(Long userId, String patternType) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM detected_patterns
                WHERE user_id = ?
                  AND pattern_type = ?
                """, Long.class, userId, patternType);
    }
}
