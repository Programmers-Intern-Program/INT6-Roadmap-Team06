package com.back.coach.domain.coach.controller;

import com.back.coach.domain.coach.service.ReplanService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Map;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReplanControllerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Mock
    private ReplanService replanService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ReplanController(replanService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void replan_whenConfirmed_returnsNewRoadmapResponse() throws Exception {
        given(replanService.confirm(1L, 10001L, 7001L))
                .willReturn(new ReplanService.ReplanResult("601", 3));

        mockMvc.perform(post("/api/coach/sessions/{sessionId}/replan", 10001L)
                        .principal(authentication(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "proposalId", 7001L,
                                "confirmed", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newRoadmapId").value("601"))
                .andExpect(jsonPath("$.data.newRoadmapVersion").value(3))
                .andExpect(jsonPath("$.data.message").value("새 로드맵이 생성됐습니다. 현재 세션은 기존 버전을 기준으로 유지됩니다."))
                .andExpect(jsonPath("$.data.dismissed").value(nullValue()))
                .andExpect(jsonPath("$.meta").isMap());

        verify(replanService).confirm(1L, 10001L, 7001L);
    }

    @Test
    void replan_whenDismissed_returnsDeferredResponse() throws Exception {
        mockMvc.perform(post("/api/coach/sessions/{sessionId}/replan", 10001L)
                        .principal(authentication(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "proposalId", 7001L,
                                "confirmed", false
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newRoadmapId").value(nullValue()))
                .andExpect(jsonPath("$.data.newRoadmapVersion").value(nullValue()))
                .andExpect(jsonPath("$.data.message").value("재계획을 보류했습니다."))
                .andExpect(jsonPath("$.data.dismissed").value(true))
                .andExpect(jsonPath("$.meta").isMap());

        verify(replanService).dismiss(1L, 7001L);
    }

    @Test
    void replan_whenRequiredFieldMissing_returnsInvalidInput() throws Exception {
        mockMvc.perform(post("/api/coach/sessions/{sessionId}/replan", 10001L)
                        .principal(authentication(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "proposalId", 7001L
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(replanService);
    }

    @Test
    void replan_whenSessionNotFound_returnsNotFound() throws Exception {
        given(replanService.confirm(1L, 10001L, 7001L))
                .willThrow(new ServiceException(ErrorCode.SESSION_NOT_FOUND));

        mockMvc.perform(post("/api/coach/sessions/{sessionId}/replan", 10001L)
                        .principal(authentication(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "proposalId", 7001L,
                                "confirmed", true
                        ))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void replan_whenProposalBelongsToAnotherUser_returnsForbidden() throws Exception {
        willThrow(new ServiceException(ErrorCode.FORBIDDEN))
                .given(replanService).dismiss(1L, 7001L);

        mockMvc.perform(post("/api/coach/sessions/{sessionId}/replan", 10001L)
                        .principal(authentication(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "proposalId", 7001L,
                                "confirmed", false
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private Authentication authentication(Long userId) {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(userId), null);
    }
}
