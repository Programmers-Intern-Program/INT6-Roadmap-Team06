package com.back.coach.api.roadmap;

import com.back.coach.domain.roadmap.RoadmapProgressCommandResult;
import com.back.coach.domain.roadmap.RoadmapProgressCommandService;
import com.back.coach.global.code.ProgressStatus;
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

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RoadmapProgressControllerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Mock
    private RoadmapProgressCommandService roadmapProgressCommandService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RoadmapProgressController(roadmapProgressCommandService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void appendProgress_whenValidRequest_returnsProgressResponse() throws Exception {
        Instant savedAt = Instant.parse("2026-04-28T01:00:00Z");
        given(roadmapProgressCommandService.appendProgress(1L, 10L, 100L, ProgressStatus.DONE, "완료"))
                .willReturn(new RoadmapProgressCommandResult(900L, 100L, ProgressStatus.DONE, savedAt));

        mockMvc.perform(post("/api/roadmaps/{roadmapId}/progress", 10L)
                        .principal(authentication(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "roadmapWeekId", 100L,
                                "status", "DONE",
                                "note", "완료"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progressLogId").value("900"))
                .andExpect(jsonPath("$.data.roadmapWeekId").value("100"))
                .andExpect(jsonPath("$.data.status").value("DONE"))
                .andExpect(jsonPath("$.data.savedAt").value("2026-04-28T01:00:00Z"))
                .andExpect(jsonPath("$.meta").isMap());

        verify(roadmapProgressCommandService).appendProgress(1L, 10L, 100L, ProgressStatus.DONE, "완료");
    }

    @Test
    void appendProgress_whenRoadmapWeekIdMissing_returnsInvalidInput() throws Exception {
        mockMvc.perform(post("/api/roadmaps/{roadmapId}/progress", 10L)
                        .principal(authentication(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "DONE"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(roadmapProgressCommandService);
    }

    @Test
    void appendProgress_whenStatusMissing_returnsInvalidInput() throws Exception {
        mockMvc.perform(post("/api/roadmaps/{roadmapId}/progress", 10L)
                        .principal(authentication(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "roadmapWeekId", 100L
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(roadmapProgressCommandService);
    }

    @Test
    void appendProgress_whenNoteIsTooLong_returnsInvalidInput() throws Exception {
        mockMvc.perform(post("/api/roadmaps/{roadmapId}/progress", 10L)
                        .principal(authentication(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "roadmapWeekId", 100L,
                                "status", "DONE",
                                "note", "a".repeat(1001)
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(roadmapProgressCommandService);
    }

    @Test
    void appendProgress_whenServiceThrowsResourceNotFound_returnsNotFound() throws Exception {
        given(roadmapProgressCommandService.appendProgress(any(), any(), any(), any(), any()))
                .willThrow(new ServiceException(ErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(post("/api/roadmaps/{roadmapId}/progress", 10L)
                        .principal(authentication(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "roadmapWeekId", 100L,
                                "status", "DONE"
                        ))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void appendProgress_whenServiceThrowsConflict_returnsConflict() throws Exception {
        given(roadmapProgressCommandService.appendProgress(any(), any(), any(), any(), any()))
                .willThrow(new ServiceException(ErrorCode.CONFLICT));

        mockMvc.perform(post("/api/roadmaps/{roadmapId}/progress", 10L)
                        .principal(authentication(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "roadmapWeekId", 100L,
                                "status", "DONE"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    private Authentication authentication(Long userId) {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(userId), null);
    }
}
