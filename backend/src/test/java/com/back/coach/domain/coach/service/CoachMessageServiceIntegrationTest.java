package com.back.coach.domain.coach.service;

import com.back.coach.domain.coach.entity.ChatSession;
import com.back.coach.domain.coach.entity.CoachConversation;
import com.back.coach.domain.coach.repository.ChatSessionRepository;
import com.back.coach.domain.coach.repository.CoachConversationRepository;
import com.back.coach.domain.context.entity.UserContextSnapshot;
import com.back.coach.domain.context.repository.UserContextSnapshotRepository;
import com.back.coach.global.code.ContextType;
import com.back.coach.domain.user.entity.User;
import com.back.coach.domain.user.repository.UserRepository;
import com.back.coach.external.llm.LlmClient;
import com.back.coach.global.code.AuthProvider;
import com.back.coach.global.code.CoachMessageRole;
import com.back.coach.global.code.CoachRoute;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.back.coach.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@IntegrationTest
class CoachMessageServiceIntegrationTest {

    @Autowired
    private CoachMessageService coachMessageService;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private CoachConversationRepository coachConversationRepository;

    @Autowired
    private UserContextSnapshotRepository contextSnapshotRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private LlmClient llmClient;

    private Long userId;
    private Long sessionId;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(
                User.signupFromOAuth(AuthProvider.GITHUB, "gh-coach-msg-" + System.nanoTime(), "msg@test.com")
        );
        userId = user.getId();

        // Slice 3 ContextManager가 PROFILE/PLAN snapshot을 version 기준으로 로드하므로 시드 필요
        contextSnapshotRepository.save(UserContextSnapshot.create(
                userId, ContextType.PROFILE, 1, "{\"goal\":\"test\"}", Instant.now()
        ));
        contextSnapshotRepository.save(UserContextSnapshot.create(
                userId, ContextType.PLAN, 1, "{\"weeks\":[]}", Instant.now()
        ));

        ChatSession session = ChatSession.start(userId, 1, 1);
        sessionId = chatSessionRepository.save(session).getId();
    }

    @AfterEach
    void cleanUp() {
        coachConversationRepository.deleteAll(
                coachConversationRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
        );
        chatSessionRepository.deleteAll(
                chatSessionRepository.findAll().stream()
                        .filter(s -> s.getUserId().equals(userId))
                        .toList()
        );
        contextSnapshotRepository.deleteAll(
                contextSnapshotRepository.findAll().stream()
                        .filter(s -> s.getUserId().equals(userId))
                        .toList()
        );
        userRepository.deleteById(userId);
    }

    @Test
    @DisplayName("USER 메시지 전송 시 USER/COACH 두 행이 저장되고 COACH는 SIMPLE_GUIDE 라우트")
    void sendMessageStoresUserAndCoachRows() {
        given(llmClient.complete(anyString(), anyString(), anyInt())).willReturn(
                "{\"route\":\"SIMPLE_GUIDE\",\"responseText\":\"이번 주는 Redis 캐시부터 학습하세요.\"}"
        );

        CoachConversation coachMessage = coachMessageService.sendMessage(userId, sessionId, "오늘 뭐 공부할까?").coachMessage();

        assertThat(coachMessage.getRole()).isEqualTo(CoachMessageRole.COACH);
        assertThat(coachMessage.getRoute()).isEqualTo(CoachRoute.SIMPLE_GUIDE);
        assertThat(coachMessage.getMessageText()).contains("Redis");

        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmClient).complete(systemCaptor.capture(), anyString(), anyInt());
        assertThat(systemCaptor.getValue()).contains("Write all user-visible natural-language values in Korean");
        assertThat(systemCaptor.getValue()).contains("responseText, replanReason, detectedIntent는 한국어로 작성");

        List<CoachConversation> rows = coachConversationRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getRole()).isEqualTo(CoachMessageRole.USER);
        assertThat(rows.get(0).getMessageText()).isEqualTo("오늘 뭐 공부할까?");
        assertThat(rows.get(1).getRole()).isEqualTo(CoachMessageRole.COACH);
    }

    @Test
    @DisplayName("닫힌 세션에 메시지 보내면 SESSION_CLOSED")
    void closedSessionRejectsMessage() {
        ChatSession session = chatSessionRepository.findById(sessionId).orElseThrow();
        session.close(Instant.now());
        chatSessionRepository.save(session);

        assertThatThrownBy(() -> coachMessageService.sendMessage(userId, sessionId, "hello"))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SESSION_CLOSED);
    }

    @Test
    @DisplayName("다른 사용자가 세션에 메시지 보내면 FORBIDDEN")
    void otherUserCannotSendMessage() {
        Long otherUserId = userId + 99999L;
        assertThatThrownBy(() -> coachMessageService.sendMessage(otherUserId, sessionId, "hello"))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("존재하지 않는 sessionId에 보내면 SESSION_NOT_FOUND")
    void nonexistentSessionFails() {
        assertThatThrownBy(() -> coachMessageService.sendMessage(userId, 999_999_999L, "hello"))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SESSION_NOT_FOUND);
    }

    @Test
    @DisplayName("LLM 실패 시 USER 메시지는 저장되지만 COACH 행은 생성되지 않음")
    void llmFailureLeavesOnlyUserMessage() {
        given(llmClient.complete(anyString(), anyString(), anyInt())).willThrow(new ServiceException(ErrorCode.LLM_TIMEOUT));

        assertThatThrownBy(() -> coachMessageService.sendMessage(userId, sessionId, "오늘 뭐 공부할까?"))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LLM_TIMEOUT);

        List<CoachConversation> rows = coachConversationRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        // @Transactional로 묶여 있어 USER 행도 롤백됨 — 시도-기록 둘 다 안 남는 게 안전
        assertThat(rows).isEmpty();
    }

    @Test
    @DisplayName("getMessages: 저장된 USER/COACH 메시지를 시간순으로 조회")
    void getMessagesReturnsStoredMessagesInTimeOrder() {
        given(llmClient.complete(anyString(), anyString(), anyInt())).willReturn(
                "{\"route\":\"SIMPLE_GUIDE\",\"responseText\":\"이번 주는 Redis 캐시부터 학습하세요.\",\"detectedIntent\":\"CHECK_TODAY_PLAN\"}"
        );
        coachMessageService.sendMessage(userId, sessionId, "오늘 뭐 공부할까?");

        List<CoachConversation> messages = coachMessageService.getMessages(userId, sessionId);

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getRole()).isEqualTo(CoachMessageRole.USER);
        assertThat(messages.get(0).getMessageText()).isEqualTo("오늘 뭐 공부할까?");
        assertThat(messages.get(1).getRole()).isEqualTo(CoachMessageRole.COACH);
        assertThat(messages.get(1).getRoute()).isEqualTo(CoachRoute.SIMPLE_GUIDE);
        assertThat(messages.get(1).getDetectedIntent()).isEqualTo("CHECK_TODAY_PLAN");
    }

    @Test
    @DisplayName("getMessages: 닫힌 세션도 히스토리 조회 가능")
    void getMessagesAllowsClosedSession() {
        given(llmClient.complete(anyString(), anyString(), anyInt())).willReturn(
                "{\"route\":\"SIMPLE_GUIDE\",\"responseText\":\"이번 주는 Redis 캐시부터 학습하세요.\"}"
        );
        coachMessageService.sendMessage(userId, sessionId, "오늘 뭐 공부할까?");
        ChatSession session = chatSessionRepository.findById(sessionId).orElseThrow();
        session.close(Instant.now());
        chatSessionRepository.save(session);

        List<CoachConversation> messages = coachMessageService.getMessages(userId, sessionId);

        assertThat(messages).hasSize(2);
    }

    @Test
    @DisplayName("getMessages: 다른 사용자의 세션이면 FORBIDDEN")
    void getMessagesByOtherUserForbidden() {
        Long otherUserId = userId + 99999L;

        assertThatThrownBy(() -> coachMessageService.getMessages(otherUserId, sessionId))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("getMessages: 존재하지 않는 sessionId면 SESSION_NOT_FOUND")
    void getMessagesNonexistentSessionFails() {
        assertThatThrownBy(() -> coachMessageService.getMessages(userId, 999_999_999L))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SESSION_NOT_FOUND);
    }
}
