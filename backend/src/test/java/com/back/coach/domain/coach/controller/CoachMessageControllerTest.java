package com.back.coach.domain.coach.controller;

import com.back.coach.domain.coach.entity.CoachConversation;
import com.back.coach.domain.coach.entity.ReplanProposal;
import com.back.coach.domain.coach.service.CoachMessageService;
import com.back.coach.global.code.CoachMessageRole;
import com.back.coach.global.code.CoachRoute;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.GlobalExceptionHandler;
import com.back.coach.global.exception.ServiceException;
import com.back.coach.global.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CoachMessageControllerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Mock
    private CoachMessageService coachMessageService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CoachMessageController(coachMessageService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void sendMessage_whenSimpleGuide_returnsCoachMessageResponse() throws Exception {
        CoachConversation coachMessage = coachMessage(
                9001L, 10001L, 1L,
                "이번 주는 Redis TTL과 캐시 무효화 개념부터 정리하는 것이 좋습니다.",
                CoachRoute.SIMPLE_GUIDE
        );
        given(coachMessageService.sendMessage(1L, 10001L, "오늘 무엇부터 공부하면 좋을까?"))
                .willReturn(new CoachMessageService.MessageResult(coachMessage, null));

        mockMvc.perform(post("/api/coach/sessions/{sessionId}/messages", 10001L)
                        .principal(authentication(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "message", "오늘 무엇부터 공부하면 좋을까?"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messageId").value("9001"))
                .andExpect(jsonPath("$.data.responseText").value("이번 주는 Redis TTL과 캐시 무효화 개념부터 정리하는 것이 좋습니다."))
                .andExpect(jsonPath("$.data.route").value("SIMPLE_GUIDE"))
                .andExpect(jsonPath("$.data.replanProposal").value(nullValue()))
                .andExpect(jsonPath("$.meta").isMap());

        verify(coachMessageService).sendMessage(1L, 10001L, "오늘 무엇부터 공부하면 좋을까?");
    }

    @Test
    void sendMessage_whenReplanSuggested_returnsProposalResponse() throws Exception {
        Instant expiresAt = Instant.parse("2026-05-08T09:00:00Z");
        CoachConversation coachMessage = coachMessage(
                9002L, 10001L, 1L,
                "3일간 Redis 학습이 완료되지 않았습니다. 로드맵을 조정해드릴까요?",
                CoachRoute.REPLAN_SUGGEST
        );
        ReplanProposal proposal = proposal(
                7001L, 10001L, 1L, 9002L,
                "3일 연속 미달성 감지 (Redis 캐시 주차)",
                expiresAt
        );
        given(coachMessageService.sendMessage(1L, 10001L, "로드맵 다시 짜야 할까?"))
                .willReturn(new CoachMessageService.MessageResult(coachMessage, proposal));

        mockMvc.perform(post("/api/coach/sessions/{sessionId}/messages", 10001L)
                        .principal(authentication(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "message", "로드맵 다시 짜야 할까?"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messageId").value("9002"))
                .andExpect(jsonPath("$.data.responseText").value("3일간 Redis 학습이 완료되지 않았습니다. 로드맵을 조정해드릴까요?"))
                .andExpect(jsonPath("$.data.route").value("REPLAN_SUGGEST"))
                .andExpect(jsonPath("$.data.replanProposal.proposalId").value("7001"))
                .andExpect(jsonPath("$.data.replanProposal.reason").value("3일 연속 미달성 감지 (Redis 캐시 주차)"))
                .andExpect(jsonPath("$.data.replanProposal.expiresAt").value("2026-05-08T09:00:00Z"))
                .andExpect(jsonPath("$.meta").isMap());
    }

    @Test
    void sendMessage_whenMessageBlank_returnsInvalidInput() throws Exception {
        mockMvc.perform(post("/api/coach/sessions/{sessionId}/messages", 10001L)
                        .principal(authentication(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "message", " "
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(coachMessageService);
    }

    @Test
    void sendMessage_whenSessionNotFound_returnsNotFound() throws Exception {
        given(coachMessageService.sendMessage(eq(1L), eq(10001L), anyString()))
                .willThrow(new ServiceException(ErrorCode.SESSION_NOT_FOUND));

        mockMvc.perform(post("/api/coach/sessions/{sessionId}/messages", 10001L)
                        .principal(authentication(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "message", "오늘 계획 알려줘"
                        ))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void sendMessage_whenSessionClosed_returnsConflict() throws Exception {
        given(coachMessageService.sendMessage(eq(1L), eq(10001L), anyString()))
                .willThrow(new ServiceException(ErrorCode.SESSION_CLOSED));

        mockMvc.perform(post("/api/coach/sessions/{sessionId}/messages", 10001L)
                        .principal(authentication(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "message", "오늘 계획 알려줘"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_CLOSED"));
    }

    @Test
    void sendMessage_whenSessionBelongsToAnotherUser_returnsForbidden() throws Exception {
        given(coachMessageService.sendMessage(eq(1L), eq(10001L), anyString()))
                .willThrow(new ServiceException(ErrorCode.FORBIDDEN));

        mockMvc.perform(post("/api/coach/sessions/{sessionId}/messages", 10001L)
                        .principal(authentication(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "message", "오늘 계획 알려줘"
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void getMessages_returnsMessagesInTimeOrder() throws Exception {
        CoachConversation userMessage = userMessage(
                9000L, 10001L, 1L,
                "오늘 무엇부터 공부하면 좋을까?",
                Instant.parse("2026-05-08T09:00:00Z")
        );
        CoachConversation coachMessage = coachMessage(
                9001L, 10001L, 1L,
                "이번 주는 Redis TTL과 캐시 무효화 개념부터 정리하는 것이 좋습니다.",
                CoachRoute.SIMPLE_GUIDE,
                Instant.parse("2026-05-08T09:00:03Z")
        );
        given(coachMessageService.getMessages(1L, 10001L))
                .willReturn(List.of(userMessage, coachMessage));

        mockMvc.perform(get("/api/coach/sessions/{sessionId}/messages", 10001L)
                        .principal(authentication(1L))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].messageId").value("9000"))
                .andExpect(jsonPath("$.data[0].role").value("USER"))
                .andExpect(jsonPath("$.data[0].messageText").value("오늘 무엇부터 공부하면 좋을까?"))
                .andExpect(jsonPath("$.data[0].route").value(nullValue()))
                .andExpect(jsonPath("$.data[0].detectedIntent").value(nullValue()))
                .andExpect(jsonPath("$.data[0].createdAt").value("2026-05-08T09:00:00Z"))
                .andExpect(jsonPath("$.data[1].messageId").value("9001"))
                .andExpect(jsonPath("$.data[1].role").value("COACH"))
                .andExpect(jsonPath("$.data[1].route").value("SIMPLE_GUIDE"))
                .andExpect(jsonPath("$.data[1].detectedIntent").value("CHECK_TODAY_PLAN"))
                .andExpect(jsonPath("$.data[1].createdAt").value("2026-05-08T09:00:03Z"))
                .andExpect(jsonPath("$.meta").isMap());

        verify(coachMessageService).getMessages(1L, 10001L);
    }

    @Test
    void getMessages_whenSessionNotFound_returnsNotFound() throws Exception {
        given(coachMessageService.getMessages(1L, 10001L))
                .willThrow(new ServiceException(ErrorCode.SESSION_NOT_FOUND));

        mockMvc.perform(get("/api/coach/sessions/{sessionId}/messages", 10001L)
                        .principal(authentication(1L))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void getMessages_whenSessionBelongsToAnotherUser_returnsForbidden() throws Exception {
        given(coachMessageService.getMessages(1L, 10001L))
                .willThrow(new ServiceException(ErrorCode.FORBIDDEN));

        mockMvc.perform(get("/api/coach/sessions/{sessionId}/messages", 10001L)
                        .principal(authentication(1L))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private CoachConversation userMessage(
            Long id,
            Long sessionId,
            Long userId,
            String messageText,
            Instant createdAt
    ) {
        CoachConversation conversation = CoachConversation.user(sessionId, userId, messageText);
        ReflectionTestUtils.setField(conversation, "id", id);
        ReflectionTestUtils.setField(conversation, "role", CoachMessageRole.USER);
        ReflectionTestUtils.setField(conversation, "createdAt", createdAt);
        return conversation;
    }

    private CoachConversation coachMessage(
            Long id,
            Long sessionId,
            Long userId,
            String responseText,
            CoachRoute route
    ) {
        return coachMessage(id, sessionId, userId, responseText, route, Instant.parse("2026-05-08T09:00:00Z"));
    }

    private CoachConversation coachMessage(
            Long id,
            Long sessionId,
            Long userId,
            String responseText,
            CoachRoute route,
            Instant createdAt
    ) {
        CoachConversation conversation = CoachConversation.coach(
                sessionId, userId, responseText, route, "CHECK_TODAY_PLAN"
        );
        ReflectionTestUtils.setField(conversation, "id", id);
        ReflectionTestUtils.setField(conversation, "createdAt", createdAt);
        return conversation;
    }

    private ReplanProposal proposal(
            Long id,
            Long sessionId,
            Long userId,
            Long messageId,
            String reason,
            Instant expiresAt
    ) {
        ReplanProposal proposal = ReplanProposal.create(sessionId, userId, messageId, reason, expiresAt);
        ReflectionTestUtils.setField(proposal, "id", id);
        return proposal;
    }

    private Authentication authentication(Long userId) {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(userId), null);
    }
}
