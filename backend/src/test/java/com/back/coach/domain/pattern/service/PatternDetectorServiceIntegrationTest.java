package com.back.coach.domain.pattern.service;

import com.back.coach.domain.user.entity.User;
import com.back.coach.domain.user.repository.UserRepository;
import com.back.coach.global.code.AuthProvider;
import com.back.coach.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class PatternDetectorServiceIntegrationTest {

    @Autowired
    private PatternDetectorService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository userRepository;

    private Long userId;
    private Long weekId;
    private Long week2Id;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(
                User.signupFromOAuth(AuthProvider.GITHUB, "gh-pattern-test-" + System.nanoTime(), "pattern@test.com")
        );
        userId = user.getId();

        Long jobRoleId = jdbc.queryForObject(
                "SELECT id FROM job_roles WHERE role_code = 'BACKEND_DEVELOPER'", Long.class);

        Long profileId = jdbc.queryForObject("""
                INSERT INTO user_profiles (user_id, job_role_id, current_level)
                VALUES (?, ?, 'JUNIOR')
                RETURNING id
                """, Long.class, userId, jobRoleId);

        Long connectionId = jdbc.queryForObject("""
                INSERT INTO github_connections (user_id, github_user_id, github_login, access_type)
                VALUES (?, 'test-gh-id', 'test-gh-login', 'OAUTH')
                RETURNING id
                """, Long.class, userId);

        Long analysisId = jdbc.queryForObject("""
                INSERT INTO github_analyses (user_id, github_connection_id, version, summary)
                VALUES (?, ?, 1, 'test analysis')
                RETURNING id
                """, Long.class, userId, connectionId);

        Long diagnosisId = jdbc.queryForObject("""
                INSERT INTO capability_diagnoses
                    (user_id, profile_id, github_analysis_id, job_role_id, version, current_level, summary)
                VALUES (?, ?, ?, ?, 1, 'JUNIOR', 'test diagnosis')
                RETURNING id
                """, Long.class, userId, profileId, analysisId, jobRoleId);

        Long roadmapId = jdbc.queryForObject("""
                INSERT INTO learning_roadmaps (user_id, diagnosis_id, version, total_weeks, summary)
                VALUES (?, ?, 1, 4, 'test roadmap')
                RETURNING id
                """, Long.class, userId, diagnosisId);

        weekId = jdbc.queryForObject("""
                INSERT INTO roadmap_weeks (roadmap_id, week_number, topic, reason_text, tasks_json, materials_json, estimated_hours)
                VALUES (?, 1, 'Redis 기초', '캐시 개념', '[]', '[]', 5.0)
                RETURNING id
                """, Long.class, roadmapId);

        week2Id = jdbc.queryForObject("""
                INSERT INTO roadmap_weeks (roadmap_id, week_number, topic, reason_text, tasks_json, materials_json, estimated_hours)
                VALUES (?, 2, 'Spring Boot', '서버 개발', '[]', '[]', 5.0)
                RETURNING id
                """, Long.class, roadmapId);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM detected_patterns WHERE user_id = ?", userId);
        userRepository.deleteById(userId);
    }

    @Test
    @DisplayName("REPEATED_INCOMPLETE: 7일 내 같은 week 비완료 3회 → MEDIUM 패턴 삽입")
    void detectsRepeatedIncompleteMedium() {
        insertProgressLog(weekId, "TODO", daysAgo(6));
        insertProgressLog(weekId, "IN_PROGRESS", daysAgo(4));
        insertProgressLog(weekId, "SKIPPED", daysAgo(2));

        service.runFullScan();

        List<Map<String, Object>> rows = findPatterns("REPEATED_INCOMPLETE");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("severity")).isEqualTo("MEDIUM");
        // PostgreSQL JSONB는 공백 포함 형태로 반환하므로 키와 값을 분리해 검증
        String metadata = rows.get(0).get("metadata").toString();
        assertThat(metadata).contains("\"count\"").contains("3")
                .contains("\"targetType\"").contains("roadmap_week");
    }

    @Test
    @DisplayName("REPEATED_INCOMPLETE: 5회 이상 → HIGH")
    void detectsRepeatedIncompleteHigh() {
        for (int i = 1; i <= 5; i++) {
            insertProgressLog(weekId, "TODO", daysAgo(i));
        }

        service.runFullScan();

        List<Map<String, Object>> rows = findPatterns("REPEATED_INCOMPLETE");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("severity")).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("REPEATED_INCOMPLETE: runFullScan 2회 실행해도 unprocessed row 1개만 존재 (idempotency)")
    void repeatedIncompleteIsIdempotent() {
        insertProgressLog(weekId, "SKIPPED", daysAgo(1));
        insertProgressLog(weekId, "SKIPPED", daysAgo(2));
        insertProgressLog(weekId, "SKIPPED", daysAgo(3));

        service.runFullScan();
        service.runFullScan();

        long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM detected_patterns WHERE user_id = ? AND pattern_type = 'REPEATED_INCOMPLETE' AND processed_at IS NULL",
                Long.class, userId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("CONSECUTIVE_DELAY: 14일 내 2개 week에 걸쳐 3회 → LOW 패턴 삽입")
    void detectsConsecutiveDelayLow() {
        insertProgressLog(weekId, "TODO", daysAgo(10));
        insertProgressLog(week2Id, "IN_PROGRESS", daysAgo(7));
        insertProgressLog(weekId, "TODO", daysAgo(3));

        service.runFullScan();

        List<Map<String, Object>> rows = findPatterns("CONSECUTIVE_DELAY");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("severity")).isEqualTo("LOW");
    }

    @Test
    @DisplayName("SKILL_REPEATED_FAILURE: 같은 topic SKIPPED 2회 → MEDIUM 패턴 삽입")
    void detectsSkillRepeatedFailureMedium() {
        insertProgressLog(weekId, "SKIPPED", daysAgo(5));
        insertProgressLog(weekId, "SKIPPED", daysAgo(2));

        service.runFullScan();

        List<Map<String, Object>> rows = findPatterns("SKILL_REPEATED_FAILURE");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("severity")).isEqualTo("MEDIUM");
        assertThat(rows.get(0).get("metadata").toString()).contains("Redis");
    }

    @Test
    @DisplayName("7일 창 바깥의 로그는 REPEATED_INCOMPLETE로 감지되지 않는다")
    void logsOutsideWindowNotDetected() {
        for (int i = 8; i <= 12; i++) {
            insertProgressLog(weekId, "TODO", daysAgo(i));
        }

        service.runFullScan();

        assertThat(findPatterns("REPEATED_INCOMPLETE")).isEmpty();
    }

    @Test
    @DisplayName("DONE 상태 로그만 있으면 어떤 패턴도 감지되지 않는다")
    void doneStatusNotDetected() {
        insertProgressLog(weekId, "DONE", daysAgo(1));
        insertProgressLog(weekId, "DONE", daysAgo(2));
        insertProgressLog(weekId, "DONE", daysAgo(3));

        service.runFullScan();

        long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM detected_patterns WHERE user_id = ?",
                Long.class, userId);
        assertThat(count).isZero();
    }

    private void insertProgressLog(Long rwId, String status, Instant createdAt) {
        jdbc.update("""
                INSERT INTO progress_logs (user_id, roadmap_week_id, status, created_at)
                VALUES (?, ?, ?, ?)
                """, userId, rwId, status, java.sql.Timestamp.from(createdAt));
    }

    private List<Map<String, Object>> findPatterns(String patternType) {
        return jdbc.queryForList(
                "SELECT * FROM detected_patterns WHERE user_id = ? AND pattern_type = ?",
                userId, patternType);
    }

    private Instant daysAgo(int days) {
        return Instant.now().minus(days, ChronoUnit.DAYS);
    }
}
