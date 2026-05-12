package com.back.coach.domain.coach.service;

import com.back.coach.domain.coach.entity.ChatSession;
import com.back.coach.domain.coach.repository.ChatSessionRepository;
import com.back.coach.domain.context.entity.UserContextSnapshot;
import com.back.coach.domain.context.repository.UserContextSnapshotRepository;
import com.back.coach.domain.user.entity.User;
import com.back.coach.domain.user.repository.UserRepository;
import com.back.coach.global.code.AuthProvider;
import com.back.coach.global.code.ChatSessionStatus;
import com.back.coach.global.code.ContextType;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.back.coach.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class CoachSessionServiceIntegrationTest {

    @Autowired
    private CoachSessionService coachSessionService;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private UserContextSnapshotRepository contextSnapshotRepository;

    @Autowired
    private UserRepository userRepository;

    private Long userId;

    @BeforeEach
    void seedUser() {
        User user = userRepository.save(
                User.signupFromOAuth(AuthProvider.GITHUB, "gh-coach-session-" + System.nanoTime(), "coach-session@test.com")
        );
        userId = user.getId();
    }

    @AfterEach
    void cleanUp() {
        chatSessionRepository.deleteAll(chatSessionRepository.findAll().stream()
                .filter(s -> s.getUserId().equals(userId))
                .toList());
        contextSnapshotRepository.deleteAll(contextSnapshotRepository.findAll().stream()
                .filter(s -> s.getUserId().equals(userId))
                .toList());
        userRepository.deleteById(userId);
    }

    @Test
    @DisplayName("active PROFILE/PLAN snapshot이 모두 있으면 세션이 시작된다")
    void startsSessionWithBothActiveSnapshots() {
        seedSnapshot(ContextType.PROFILE, 1);
        seedSnapshot(ContextType.PLAN, 2);

        ChatSession session = coachSessionService.startSession(userId);

        assertThat(session.getId()).isNotNull();
        assertThat(session.getProfileVersion()).isEqualTo(1);
        assertThat(session.getRoadmapVersion()).isEqualTo(2);
        assertThat(session.getStatus()).isEqualTo(ChatSessionStatus.ACTIVE);
        assertThat(session.getStartedAt()).isNotNull();
        assertThat(session.getEndedAt()).isNull();
    }

    @Test
    @DisplayName("PROFILE snapshot이 없으면 SNAPSHOT_NOT_FOUND")
    void failsWhenProfileSnapshotMissing() {
        seedSnapshot(ContextType.PLAN, 1);

        assertThatThrownBy(() -> coachSessionService.startSession(userId))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SNAPSHOT_NOT_FOUND);
    }

    @Test
    @DisplayName("PLAN snapshot이 없으면 SNAPSHOT_NOT_FOUND")
    void failsWhenPlanSnapshotMissing() {
        seedSnapshot(ContextType.PROFILE, 1);

        assertThatThrownBy(() -> coachSessionService.startSession(userId))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SNAPSHOT_NOT_FOUND);
    }

    @Test
    @DisplayName("세션 종료 시 status=CLOSED, ended_at 기록")
    void closeSessionMarksClosedAndEnded() {
        seedSnapshot(ContextType.PROFILE, 1);
        seedSnapshot(ContextType.PLAN, 1);
        ChatSession session = coachSessionService.startSession(userId);

        coachSessionService.closeSession(userId, session.getId());

        ChatSession reloaded = chatSessionRepository.findById(session.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ChatSessionStatus.CLOSED);
        assertThat(reloaded.getEndedAt()).isNotNull();
    }

    @Test
    @DisplayName("다른 사용자의 세션을 종료하려 하면 FORBIDDEN")
    void closeSessionByOtherUserForbidden() {
        seedSnapshot(ContextType.PROFILE, 1);
        seedSnapshot(ContextType.PLAN, 1);
        ChatSession session = coachSessionService.startSession(userId);

        Long otherUserId = userId + 99999L;

        assertThatThrownBy(() -> coachSessionService.closeSession(otherUserId, session.getId()))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("존재하지 않는 sessionId 종료 시 SESSION_NOT_FOUND")
    void closeNonexistentSession() {
        assertThatThrownBy(() -> coachSessionService.closeSession(userId, 999_999_999L))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SESSION_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 종료된 세션 재종료해도 에러 없이 멱등")
    void closeAlreadyClosedSessionIsIdempotent() {
        seedSnapshot(ContextType.PROFILE, 1);
        seedSnapshot(ContextType.PLAN, 1);
        ChatSession session = coachSessionService.startSession(userId);

        coachSessionService.closeSession(userId, session.getId());
        coachSessionService.closeSession(userId, session.getId());

        ChatSession reloaded = chatSessionRepository.findById(session.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ChatSessionStatus.CLOSED);
    }

    @Test
    @DisplayName("getActiveSession: ACTIVE 세션이 있으면 해당 세션 반환")
    void getActiveSessionReturnsActive() {
        seedSnapshot(ContextType.PROFILE, 1);
        seedSnapshot(ContextType.PLAN, 1);
        ChatSession started = coachSessionService.startSession(userId);

        ChatSession active = coachSessionService.getActiveSession(userId);

        assertThat(active.getId()).isEqualTo(started.getId());
        assertThat(active.getStatus()).isEqualTo(ChatSessionStatus.ACTIVE);
    }

    @Test
    @DisplayName("getActiveSession: ACTIVE/CLOSED 혼재 시 가장 최근 ACTIVE 반환")
    void getActiveSessionReturnsMostRecentActiveAmongMixed() {
        seedSnapshot(ContextType.PROFILE, 1);
        seedSnapshot(ContextType.PLAN, 1);
        ChatSession older = coachSessionService.startSession(userId);
        coachSessionService.closeSession(userId, older.getId());
        ChatSession newerActive = coachSessionService.startSession(userId);

        ChatSession active = coachSessionService.getActiveSession(userId);

        assertThat(active.getId()).isEqualTo(newerActive.getId());
        assertThat(active.getStatus()).isEqualTo(ChatSessionStatus.ACTIVE);
    }

    @Test
    @DisplayName("self-heal: PROFILE snapshot이 placeholder면 startSession이 publisher를 강제 호출해 새 version을 만든다")
    void selfHealRefreshesPlaceholderProfileSnapshot() {
        // placeholder payload(< 500 bytes) — SQL seed / migration 흔적 가정
        seedSnapshot(ContextType.PROFILE, 1, "{}");
        seedSnapshot(ContextType.PLAN, 1);

        ChatSession session = coachSessionService.startSession(userId);

        // self-heal 호출이 PROFILE을 새 version으로 발행했으므로, 세션은 v2 이상에 pin된다
        assertThat(session.getProfileVersion()).isGreaterThan(1);
        // PLAN은 padded(>=500 bytes)이므로 self-heal 안 됨 → v1 유지
        assertThat(session.getRoadmapVersion()).isEqualTo(1);
        // 새 PROFILE active snapshot이 placeholder가 아니어야 함 (이번 환경에선 publisher가 채워줌)
        UserContextSnapshot active = contextSnapshotRepository
                .findActiveByUserIdAndContextType(userId, ContextType.PROFILE)
                .orElseThrow();
        assertThat(active.getVersion()).isEqualTo(session.getProfileVersion());
    }

    @Test
    @DisplayName("self-heal: PLAN snapshot이 placeholder면 startSession이 publisher를 강제 호출해 새 version을 만든다")
    void selfHealRefreshesPlaceholderPlanSnapshot() {
        seedSnapshot(ContextType.PROFILE, 1);
        seedSnapshot(ContextType.PLAN, 1, "{}");

        ChatSession session = coachSessionService.startSession(userId);

        assertThat(session.getProfileVersion()).isEqualTo(1);
        assertThat(session.getRoadmapVersion()).isGreaterThan(1);
    }

    @Test
    @DisplayName("self-heal: 두 snapshot 모두 풍부하면 publisher가 호출되지 않고 기존 version에 pin")
    void noSelfHealWhenSnapshotsAlreadyRich() {
        seedSnapshot(ContextType.PROFILE, 7);
        seedSnapshot(ContextType.PLAN, 5);

        ChatSession session = coachSessionService.startSession(userId);

        // 풍부한 payload → publisher 안 돔 → seed한 version 그대로
        assertThat(session.getProfileVersion()).isEqualTo(7);
        assertThat(session.getRoadmapVersion()).isEqualTo(5);
    }

    @Test
    @DisplayName("getActiveSession: ACTIVE 세션이 없으면 SESSION_NOT_FOUND")
    void getActiveSessionThrowsWhenNoActive() {
        assertThatThrownBy(() -> coachSessionService.getActiveSession(userId))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SESSION_NOT_FOUND);
    }

    @Test
    @DisplayName("getSessions: ACTIVE/CLOSED 세션을 최신순으로 반환")
    void getSessionsReturnsActiveAndClosedSessionsLatestFirst() {
        seedSnapshot(ContextType.PROFILE, 1);
        seedSnapshot(ContextType.PLAN, 1);
        ChatSession older = coachSessionService.startSession(userId);
        coachSessionService.closeSession(userId, older.getId());
        ChatSession newer = coachSessionService.startSession(userId);

        List<ChatSession> sessions = coachSessionService.getSessions(userId);

        assertThat(sessions).extracting(ChatSession::getId)
                .containsExactly(newer.getId(), older.getId());
        assertThat(sessions).extracting(ChatSession::getStatus)
                .containsExactly(ChatSessionStatus.ACTIVE, ChatSessionStatus.CLOSED);
    }

    @Test
    @DisplayName("getSessions: 세션이 없으면 빈 목록")
    void getSessionsReturnsEmptyListWhenNoSession() {
        List<ChatSession> sessions = coachSessionService.getSessions(userId);

        assertThat(sessions).isEmpty();
    }

    private void seedSnapshot(ContextType type, int version) {
        // CoachSessionService.startSession은 payload < 500 bytes를 placeholder로 보고 self-heal publish를 트리거한다.
        // 기존 테스트들은 "이 seed version으로 세션이 시작된다"를 검증하므로, 실데이터 사이즈를 흉내내는 padding payload를 사용한다.
        seedSnapshot(type, version, paddedPayload(type));
    }

    private void seedSnapshot(ContextType type, int version, String payload) {
        UserContextSnapshot snapshot = UserContextSnapshot.create(
                userId, type, version, payload, Instant.now()
        );
        contextSnapshotRepository.save(snapshot);
    }

    private static String paddedPayload(ContextType type) {
        // 500 bytes 이상이면 publisher self-heal이 트리거되지 않는다 (PLACEHOLDER_PAYLOAD_BYTES_THRESHOLD).
        return "{\"contextType\":\"" + type.name() + "\",\"_padding\":\"" + "x".repeat(600) + "\"}";
    }
}
