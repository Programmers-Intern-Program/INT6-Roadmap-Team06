package com.back.coach.domain.context.service;

import com.back.coach.domain.context.entity.UserContextSnapshot;
import com.back.coach.domain.context.repository.UserContextSnapshotRepository;
import com.back.coach.domain.user.entity.User;
import com.back.coach.domain.user.repository.UserRepository;
import com.back.coach.global.code.AuthProvider;
import com.back.coach.global.code.ContextType;
import com.back.coach.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class ContextSnapshotPublisherIntegrationTest {

    @Autowired
    private ContextSnapshotPublisher publisher;

    @Autowired
    private UserContextSnapshotRepository snapshotRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private Long userId;

    @BeforeEach
    void seedUser() {
        User user = userRepository.save(
                User.signupFromOAuth(AuthProvider.GITHUB,
                        "gh-snapshot-pub-" + System.nanoTime(),
                        "snapshot-pub@test.com")
        );
        userId = user.getId();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM roadmap_weeks WHERE roadmap_id IN (SELECT id FROM learning_roadmaps WHERE user_id = ?)", userId);
        jdbc.update("DELETE FROM learning_roadmaps WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM capability_diagnoses WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM github_analyses WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM github_connections WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM user_profiles WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM job_roles WHERE role_code LIKE 'TEST_SNAPSHOT_%'");
        snapshotRepository.deleteAll(snapshotRepository.findAll().stream()
                .filter(s -> s.getUserId().equals(userId))
                .toList());
        userRepository.deleteById(userId);
    }

    @Test
    @DisplayName("publishProfile: 활성 트랜잭션 없이 호출 시 PROFILE snapshot 1건 active로 저장")
    void publishProfileCreatesActiveSnapshot() {
        publisher.publishProfile(userId);

        Optional<UserContextSnapshot> active =
                snapshotRepository.findActiveByUserIdAndContextType(userId, ContextType.PROFILE);
        assertThat(active).isPresent();
        assertThat(active.get().getVersion()).isEqualTo(1);
        assertThat(active.get().getValidTo()).isNull();
    }

    @Test
    @DisplayName("publishPlan: PLAN snapshot에 로드맵 주차별 tasks/materials를 포함한다")
    void publishPlanIncludesRoadmapWeeksTasksAndMaterials() throws Exception {
        Long roadmapId = seedRoadmapWithWeeks();

        publisher.publishPlan(userId);

        UserContextSnapshot active = snapshotRepository
                .findActiveByUserIdAndContextType(userId, ContextType.PLAN)
                .orElseThrow();
        JsonNode root = objectMapper.readTree(active.getPayload());

        assertThat(root.path("sourceRefs").path("roadmapId").asText()).isEqualTo(String.valueOf(roadmapId));
        assertThat(root.path("sourceRefs").path("roadmapVersion").asInt()).isEqualTo(3);
        assertThat(root.path("sourceRefs").path("diagnosisVersion").asInt()).isEqualTo(2);
        assertThat(root.path("roadmap").path("summary").asText()).isEqualTo("Java 기초와 Spring 입문 로드맵");
        assertThat(root.path("roadmap").path("totalWeeks").asInt()).isEqualTo(4);

        JsonNode weeks = root.path("roadmap").path("weeks");
        assertThat(weeks.size()).isEqualTo(2);
        assertThat(weeks.get(0).path("weekNumber").asInt()).isEqualTo(1);
        assertThat(weeks.get(0).path("topic").asText()).isEqualTo("Java 기초 문법");
        assertThat(weeks.get(0).path("reason").asText()).contains("기초 문법");
        assertThat(weeks.get(0).path("estimatedHours").decimalValue()).isEqualByComparingTo("5.5");
        assertThat(weeks.get(0).path("tasks").get(0).path("title").asText()).isEqualTo("Optional 예제 구현");
        assertThat(weeks.get(0).path("materials").get(0).path("url").asText()).isEqualTo("https://docs.oracle.com/javase/tutorial/");
        assertThat(weeks.get(1).path("tasks").size()).isZero();
    }

    @Test
    @DisplayName("publishPlan: 활성 트랜잭션 없이 호출 시 PLAN snapshot 1건 active로 저장")
    void publishPlanCreatesActiveSnapshot() {
        publisher.publishPlan(userId);

        Optional<UserContextSnapshot> active =
                snapshotRepository.findActiveByUserIdAndContextType(userId, ContextType.PLAN);
        assertThat(active).isPresent();
        assertThat(active.get().getVersion()).isEqualTo(1);
        assertThat(active.get().getValidTo()).isNull();
    }

    @Test
    @DisplayName("publishProfile: 활성 트랜잭션 afterCommit 이후에도 PROFILE snapshot이 저장된다")
    void publishProfileInsideTransactionCreatesSnapshotAfterCommit() {
        transactionTemplate.executeWithoutResult(status -> publisher.publishProfile(userId));

        Optional<UserContextSnapshot> active =
                snapshotRepository.findActiveByUserIdAndContextType(userId, ContextType.PROFILE);
        assertThat(active).isPresent();
        assertThat(active.get().getVersion()).isEqualTo(1);
        assertThat(active.get().getValidTo()).isNull();
    }

    @Test
    @DisplayName("publishProfile 두 번 호출 시 이전 snapshot은 close되고 새 version active")
    void publishProfileTwiceClosesPreviousVersion() {
        publisher.publishProfile(userId);
        publisher.publishProfile(userId);

        long total = snapshotRepository.findAll().stream()
                .filter(s -> s.getUserId().equals(userId))
                .filter(s -> s.getContextType() == ContextType.PROFILE)
                .count();
        assertThat(total).isEqualTo(2);

        Optional<UserContextSnapshot> active =
                snapshotRepository.findActiveByUserIdAndContextType(userId, ContextType.PROFILE);
        assertThat(active).isPresent();
        assertThat(active.get().getVersion()).isEqualTo(2);
    }

    private Long seedRoadmapWithWeeks() {
        String suffix = String.valueOf(System.nanoTime());
        Long jobRoleId = jdbc.queryForObject("""
                INSERT INTO job_roles (role_code, role_name, description)
                VALUES (?, ?, ?)
                RETURNING id
                """, Long.class, "TEST_SNAPSHOT_" + suffix, "테스트 백엔드 " + suffix, "test");

        Long profileId = jdbc.queryForObject("""
                INSERT INTO user_profiles (user_id, job_role_id, current_level, weekly_study_hours, interest_areas_json)
                VALUES (?, ?, 'BASIC', 8, ?::jsonb)
                RETURNING id
                """, Long.class, userId, jobRoleId, "[\"Java\", \"Spring\"]");

        Long connectionId = jdbc.queryForObject("""
                INSERT INTO github_connections (user_id, github_user_id, github_login, access_type)
                VALUES (?, ?, ?, 'OAUTH')
                RETURNING id
                """, Long.class, userId, "snapshot-" + suffix, "snapshot-" + suffix);

        Long analysisId = jdbc.queryForObject("""
                INSERT INTO github_analyses (user_id, github_connection_id, version, summary, analysis_payload)
                VALUES (?, ?, 1, 'GitHub 분석 요약', ?::jsonb)
                RETURNING id
                """, Long.class, userId, connectionId, "{}");

        Long diagnosisId = jdbc.queryForObject("""
                INSERT INTO capability_diagnoses (
                  user_id, profile_id, github_analysis_id, job_role_id, version, current_level, summary, diagnosis_payload
                )
                VALUES (?, ?, ?, ?, 2, 'BASIC', 'Java 기초 보강 필요', ?::jsonb)
                RETURNING id
                """, Long.class, userId, profileId, analysisId, jobRoleId, "{}");

        Long roadmapId = jdbc.queryForObject("""
                INSERT INTO learning_roadmaps (user_id, diagnosis_id, version, total_weeks, summary, roadmap_payload)
                VALUES (?, ?, 3, 4, 'Java 기초와 Spring 입문 로드맵', ?::jsonb)
                RETURNING id
                """, Long.class, userId, diagnosisId, "{}");

        jdbc.update("""
                INSERT INTO roadmap_weeks (
                  roadmap_id, week_number, topic, reason_text, tasks_json, materials_json, estimated_hours
                )
                VALUES (?, 1, 'Java 기초 문법', '기초 문법을 먼저 잡아야 Spring 예제를 이해할 수 있음',
                        ?::jsonb, ?::jsonb, 5.5)
                """, roadmapId,
                "[{\"title\":\"Optional 예제 구현\",\"type\":\"example\",\"description\":\"NPE 방어 메서드 작성\"}]",
                "[{\"title\":\"Oracle Java Tutorial\",\"type\":\"docs\",\"url\":\"https://docs.oracle.com/javase/tutorial/\"}]");
        jdbc.update("""
                INSERT INTO roadmap_weeks (
                  roadmap_id, week_number, topic, reason_text, tasks_json, materials_json, estimated_hours
                )
                VALUES (?, 2, 'Spring Controller 실습', 'HTTP 요청 흐름을 작은 API로 확인',
                        ?::jsonb, ?::jsonb, 4.0)
                """, roadmapId, "{}", "[]");

        return roadmapId;
    }
}
