package com.back.coach.domain.coach.controller;

import com.back.coach.domain.coach.entity.ChatSession;
import com.back.coach.domain.coach.service.CoachSessionService;
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

import java.time.Instant;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CoachSessionControllerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Mock
    private CoachSessionService coachSessionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CoachSessionController(coachSessionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void startSession_whenSnapshotsExist_returnsSessionResponse() throws Exception {
        ChatSession session = session(10001L, 1L, 3, 2, Instant.parse("2026-05-07T09:00:00Z"));
        given(coachSessionService.startSession(1L)).willReturn(session);

        mockMvc.perform(post("/api/coach/sessions")
                        .principal(authentication(1L))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sessionId").value("10001"))
                .andExpect(jsonPath("$.data.profileVersion").value(3))
                .andExpect(jsonPath("$.data.roadmapVersion").value(2))
                .andExpect(jsonPath("$.data.startedAt").value("2026-05-07T09:00:00Z"))
                .andExpect(jsonPath("$.meta").isMap());

        verify(coachSessionService).startSession(1L);
    }

    @Test
    void startSession_whenSnapshotNotFound_returnsNotFound() throws Exception {
        given(coachSessionService.startSession(1L))
                .willThrow(new ServiceException(ErrorCode.SNAPSHOT_NOT_FOUND));

        mockMvc.perform(post("/api/coach/sessions")
                        .principal(authentication(1L))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SNAPSHOT_NOT_FOUND"));
    }

    @Test
    void getActiveSession_whenActiveSessionExists_returnsSessionResponse() throws Exception {
        ChatSession session = session(10001L, 1L, 3, 2, Instant.parse("2026-05-07T09:00:00Z"));
        given(coachSessionService.getActiveSession(1L)).willReturn(session);

        mockMvc.perform(get("/api/coach/sessions/active")
                        .principal(authentication(1L))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value("10001"))
                .andExpect(jsonPath("$.data.profileVersion").value(3))
                .andExpect(jsonPath("$.data.roadmapVersion").value(2))
                .andExpect(jsonPath("$.data.startedAt").value("2026-05-07T09:00:00Z"));

        verify(coachSessionService).getActiveSession(1L);
    }

    @Test
    void getActiveSession_whenNoActiveSession_returnsNotFound() throws Exception {
        given(coachSessionService.getActiveSession(1L))
                .willThrow(new ServiceException(ErrorCode.SESSION_NOT_FOUND));

        mockMvc.perform(get("/api/coach/sessions/active")
                        .principal(authentication(1L))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void closeSession_whenSessionExists_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/coach/sessions/{sessionId}", 10001L)
                        .principal(authentication(1L)))
                .andExpect(status().isNoContent());

        verify(coachSessionService).closeSession(1L, 10001L);
    }

    @Test
    void closeSession_whenSessionNotFound_returnsNotFound() throws Exception {
        willThrow(new ServiceException(ErrorCode.SESSION_NOT_FOUND))
                .given(coachSessionService).closeSession(1L, 10001L);

        mockMvc.perform(delete("/api/coach/sessions/{sessionId}", 10001L)
                        .principal(authentication(1L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void closeSession_whenSessionBelongsToAnotherUser_returnsForbidden() throws Exception {
        willThrow(new ServiceException(ErrorCode.FORBIDDEN))
                .given(coachSessionService).closeSession(1L, 10001L);

        mockMvc.perform(delete("/api/coach/sessions/{sessionId}", 10001L)
                        .principal(authentication(1L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private ChatSession session(Long id, Long userId, Integer profileVersion, Integer roadmapVersion, Instant startedAt) {
        ChatSession session = ChatSession.start(userId, profileVersion, roadmapVersion);
        ReflectionTestUtils.setField(session, "id", id);
        ReflectionTestUtils.setField(session, "startedAt", startedAt);
        return session;
    }

    private Authentication authentication(Long userId) {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(userId), null);
    }
}
