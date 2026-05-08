package com.back.coach.domain.context.service;

import com.back.coach.domain.coach.entity.ChatSession;
import com.back.coach.domain.coach.repository.ChatSessionRepository;
import com.back.coach.domain.context.entity.UserContextSnapshot;
import com.back.coach.domain.context.repository.UserContextSnapshotRepository;
import com.back.coach.domain.pattern.entity.DetectedPattern;
import com.back.coach.domain.pattern.repository.DetectedPatternRepository;
import com.back.coach.domain.user.entity.User;
import com.back.coach.domain.user.repository.UserRepository;
import com.back.coach.global.code.AuthProvider;
import com.back.coach.global.code.CoachTemplate;
import com.back.coach.global.code.ContextType;
import com.back.coach.global.code.PatternSeverity;
import com.back.coach.global.code.PatternType;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.back.coach.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class ContextManagerServiceIntegrationTest {

    @Autowired
    private ContextManagerService contextManagerService;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private UserContextSnapshotRepository contextSnapshotRepository;

    @Autowired
    private DetectedPatternRepository detectedPatternRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private Long userId;

    @BeforeEach
    void seedUser() {
        User user = userRepository.save(
                User.signupFromOAuth(AuthProvider.GITHUB, "gh-ctxmgr-" + System.nanoTime(), "ctxmgr@test.com")
        );
        userId = user.getId();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM detected_patterns WHERE user_id = ?", userId);
        chatSessionRepository.deleteAll(chatSessionRepository.findAll().stream()
                .filter(s -> s.getUserId().equals(userId))
                .toList());
        contextSnapshotRepository.deleteAll(contextSnapshotRepository.findAll().stream()
                .filter(s -> s.getUserId().equals(userId))
                .toList());
        userRepository.deleteById(userId);
    }

    @Test
    @DisplayName("Tier 1: PROFILE/PLAN snapshot만 포함, 활성 신호 섹션 없음")
    void tier1ContainsProfileAndPlanOnly() {
        seedSnapshot(ContextType.PROFILE, 1, "{\"job\":\"BACKEND\"}");
        seedSnapshot(ContextType.PLAN, 1, "{\"weeks\":4}");
        ChatSession session = startSession(1, 1);

        AssembledContext ctx = contextManagerService.assemble(
                session, CoachTemplate.COACH_LIGHTWEIGHT, "오늘 뭐 공부해?"
        );

        assertThat(ctx.template()).isEqualTo(CoachTemplate.COACH_LIGHTWEIGHT);
        assertThat(ctx.systemPrompt()).contains("# 사용자 프로필");
        assertThat(ctx.systemPrompt()).contains("BACKEND");
        assertThat(ctx.systemPrompt()).contains("# 학습 로드맵");
        assertThat(ctx.systemPrompt()).doesNotContain("# 활성 신호");
        assertThat(ctx.systemPrompt()).contains("# 사용자 메시지").contains("오늘 뭐 공부해?");
    }

    @Test
    @DisplayName("Tier 3: Tier 1 + 활성 신호 섹션 포함")
    void tier3IncludesActiveSignals() {
        seedSnapshot(ContextType.PROFILE, 2, "{}");
        seedSnapshot(ContextType.PLAN, 3, "{}");
        seedDetectedPattern(PatternType.REPEATED_INCOMPLETE, PatternSeverity.MEDIUM,
                "{\"count\":3,\"targetType\":\"roadmap_week\",\"targetId\":12}");
        ChatSession session = startSession(2, 3);

        AssembledContext ctx = contextManagerService.assemble(
                session, CoachTemplate.COACH_FULL_CONTEXT, "재계획 필요해?"
        );

        assertThat(ctx.template()).isEqualTo(CoachTemplate.COACH_FULL_CONTEXT);
        assertThat(ctx.activeSignalCount()).isEqualTo(1);
        assertThat(ctx.systemPrompt()).contains("# 활성 신호");
        assertThat(ctx.systemPrompt()).contains("REPEATED_INCOMPLETE");
        assertThat(ctx.systemPrompt()).contains("MEDIUM");
    }

    @Test
    @DisplayName("CONVERSATION activeSignals가 있으면 detected_patterns fallback보다 우선")
    void conversationActiveSignalsTakePrecedenceOverDetectedPatternsFallback() {
        seedSnapshot(ContextType.PROFILE, 1, "{}");
        seedSnapshot(ContextType.PLAN, 1, "{}");
        seedConversationSnapshot("""
                {
                  "contextType": "CONVERSATION",
                  "sourceRefs": {
                    "sessionId": "10",
                    "profileSnapshotVersion": 1,
                    "planSnapshotVersion": 1
                  },
                  "activeSignals": [
                    {
                      "sourcePatternId": 31,
                      "patternType": "REPEATED_INCOMPLETE",
                      "severity": "MEDIUM",
                      "summary": "Redis 2주차 과제가 최근 3회 미완료 상태로 남아 있음",
                      "detectedAt": "2026-05-06T00:00:00Z"
                    }
                  ]
                }
                """);
        seedDetectedPattern(PatternType.SKILL_REPEATED_FAILURE, PatternSeverity.HIGH,
                "{\"skill\":\"Spring Security\"}");
        ChatSession session = startSession(1, 1);

        AssembledContext ctx = contextManagerService.assembleAuto(session, "재계획 필요해?");

        assertThat(ctx.template()).isEqualTo(CoachTemplate.COACH_FULL_CONTEXT);
        assertThat(ctx.activeSignalCount()).isEqualTo(1);
        assertThat(ctx.systemPrompt()).contains("# 활성 신호 (activeSignals)");
        assertThat(ctx.systemPrompt()).contains("sourcePatternId=31");
        assertThat(ctx.systemPrompt()).contains("Redis 2주차 과제가 최근 3회 미완료 상태로 남아 있음");
        assertThat(ctx.systemPrompt()).doesNotContain("Spring Security");
        assertThat(ctx.systemPrompt()).doesNotContain("Pattern Detector 미처리");
    }

    @Test
    @DisplayName("CONVERSATION activeSignals만 있어도 자동 선택은 Tier 3")
    void assembleAutoChoosesTier3WhenConversationActiveSignalsExist() {
        seedSnapshot(ContextType.PROFILE, 1, "{}");
        seedSnapshot(ContextType.PLAN, 1, "{}");
        seedConversationSnapshot("""
                {
                  "contextType": "CONVERSATION",
                  "sourceRefs": {
                    "sessionId": "10",
                    "profileSnapshotVersion": 1,
                    "planSnapshotVersion": 1
                  },
                  "activeSignals": [
                    {
                      "sourcePatternId": 41,
                      "patternType": "GOAL_DRIFT_CANDIDATE",
                      "severity": "LOW",
                      "summary": "목표 직무와 다른 학습 주제가 반복됨",
                      "detectedAt": "2026-05-06T01:00:00Z"
                    }
                  ]
                }
                """);
        ChatSession session = startSession(1, 1);

        AssembledContext ctx = contextManagerService.assembleAuto(session, "요즘 방향이 맞아?");

        assertThat(ctx.template()).isEqualTo(CoachTemplate.COACH_FULL_CONTEXT);
        assertThat(ctx.activeSignalCount()).isEqualTo(1);
        assertThat(ctx.systemPrompt()).contains("목표 직무와 다른 학습 주제가 반복됨");
    }

    @Test
    @DisplayName("CONVERSATION activeSignals가 비어 있으면 detected_patterns fallback 사용")
    void emptyConversationActiveSignalsFallBackToDetectedPatterns() {
        seedSnapshot(ContextType.PROFILE, 1, "{}");
        seedSnapshot(ContextType.PLAN, 1, "{}");
        seedConversationSnapshot("""
                {
                  "contextType": "CONVERSATION",
                  "sourceRefs": {
                    "sessionId": "10",
                    "profileSnapshotVersion": 1,
                    "planSnapshotVersion": 1
                  },
                  "activeSignals": []
                }
                """);
        seedDetectedPattern(PatternType.CONSECUTIVE_DELAY, PatternSeverity.MEDIUM,
                "{\"targetType\":\"roadmap_week\",\"targetId\":12}");
        ChatSession session = startSession(1, 1);

        AssembledContext ctx = contextManagerService.assembleAuto(session, "밀린 것 같아");

        assertThat(ctx.template()).isEqualTo(CoachTemplate.COACH_FULL_CONTEXT);
        assertThat(ctx.activeSignalCount()).isEqualTo(1);
        assertThat(ctx.systemPrompt()).contains("CONSECUTIVE_DELAY");
        assertThat(ctx.systemPrompt()).contains("targetType");
        assertThat(ctx.systemPrompt()).contains("roadmap_week");
        assertThat(ctx.systemPrompt()).contains("targetId");
    }

    @Test
    @DisplayName("자동 선택: 활성 신호 없으면 Tier 1")
    void assembleAutoChoosesTier1WhenNoActiveSignal() {
        seedSnapshot(ContextType.PROFILE, 1, "{}");
        seedSnapshot(ContextType.PLAN, 1, "{}");
        ChatSession session = startSession(1, 1);

        AssembledContext ctx = contextManagerService.assembleAuto(session, "hello");

        assertThat(ctx.template()).isEqualTo(CoachTemplate.COACH_LIGHTWEIGHT);
        assertThat(ctx.activeSignalCount()).isZero();
    }

    @Test
    @DisplayName("자동 선택: 활성 신호 있으면 Tier 3")
    void assembleAutoChoosesTier3WhenActiveSignalExists() {
        seedSnapshot(ContextType.PROFILE, 1, "{}");
        seedSnapshot(ContextType.PLAN, 1, "{}");
        seedDetectedPattern(PatternType.SKILL_REPEATED_FAILURE, PatternSeverity.HIGH, "{}");
        ChatSession session = startSession(1, 1);

        AssembledContext ctx = contextManagerService.assembleAuto(session, "hello");

        assertThat(ctx.template()).isEqualTo(CoachTemplate.COACH_FULL_CONTEXT);
        assertThat(ctx.activeSignalCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("세션 고정 version 기준으로 조립 — active와 다른 version도 정확히 로드")
    void assemblesByPinnedVersionNotActive() {
        // version 1, 2가 모두 존재. active는 v2지만 세션은 v1으로 고정.
        seedSnapshot(ContextType.PROFILE, 1, "{\"v\":\"old\"}");
        seedSnapshot(ContextType.PROFILE, 2, "{\"v\":\"new\"}");
        seedSnapshot(ContextType.PLAN, 1, "{\"weeks\":4}");
        ChatSession session = startSession(1, 1);

        AssembledContext ctx = contextManagerService.assemble(
                session, CoachTemplate.COACH_LIGHTWEIGHT, "hello"
        );

        assertThat(ctx.systemPrompt()).contains("\"old\"");
        assertThat(ctx.systemPrompt()).doesNotContain("\"new\"");
    }

    @Test
    @DisplayName("고정된 version의 snapshot이 없으면 SNAPSHOT_NOT_FOUND")
    void missingPinnedSnapshotThrows() {
        seedSnapshot(ContextType.PROFILE, 1, "{}");
        // PLAN version=1 없음
        ChatSession session = startSession(1, 1);

        assertThatThrownBy(() -> contextManagerService.assemble(
                session, CoachTemplate.COACH_LIGHTWEIGHT, "hello"
        ))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SNAPSHOT_NOT_FOUND);
    }

    private void seedSnapshot(ContextType type, int version, String payload) {
        contextSnapshotRepository.save(UserContextSnapshot.create(
                userId, type, version, payload, Instant.now()
        ));
    }

    private void seedDetectedPattern(PatternType type, PatternSeverity severity, String metadata) {
        jdbc.update("""
                INSERT INTO detected_patterns (user_id, pattern_type, severity, metadata, idempotency_key)
                VALUES (?, ?, ?, ?::jsonb, ?)
                """, userId, type.name(), severity.name(), metadata, "test-" + System.nanoTime());
    }

    private void seedConversationSnapshot(String payload) {
        contextSnapshotRepository.save(UserContextSnapshot.create(
                userId, ContextType.CONVERSATION, 1, payload, Instant.now()
        ));
    }

    private ChatSession startSession(int profileVersion, int planVersion) {
        return chatSessionRepository.save(ChatSession.start(userId, profileVersion, planVersion));
    }
}
