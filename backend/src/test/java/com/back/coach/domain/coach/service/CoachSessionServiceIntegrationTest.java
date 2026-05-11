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
    @DisplayName("getActiveSession: ACTIVE 세션이 없으면 SESSION_NOT_FOUND")
    void getActiveSessionThrowsWhenNoActive() {
        assertThatThrownBy(() -> coachSessionService.getActiveSession(userId))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SESSION_NOT_FOUND);
    }

    private void seedSnapshot(ContextType type, int version) {
        UserContextSnapshot snapshot = UserContextSnapshot.create(
                userId, type, version, "{}", Instant.now()
        );
        contextSnapshotRepository.save(snapshot);
    }
}
