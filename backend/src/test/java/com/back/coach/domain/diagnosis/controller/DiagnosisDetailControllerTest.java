package com.back.coach.domain.diagnosis.controller;

import com.back.coach.domain.diagnosis.dto.DiagnosisDetailResponse;
import com.back.coach.domain.diagnosis.service.DiagnosisDetailService;
import com.back.coach.global.code.CurrentLevel;
import com.back.coach.global.code.DiagnosisSeverity;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DiagnosisDetailControllerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Mock
    private DiagnosisDetailService diagnosisDetailService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DiagnosisDetailController(diagnosisDetailService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void findDiagnosis_whenDiagnosisExists_returnsDiagnosisDetail() throws Exception {
        given(diagnosisDetailService.findDiagnosis(1L, 30L))
                .willReturn(new DiagnosisDetailResponse(
                        "30",
                        3,
                        "10",
                        "20",
                        "BACKEND_ENGINEER",
                        CurrentLevel.JUNIOR,
                        "Redis 보완 필요",
                        List.of(new DiagnosisDetailResponse.MissingSkillResponse(
                                "Redis",
                                DiagnosisSeverity.HIGH,
                                "캐시 설계 경험이 부족함",
                                1
                        )),
                        List.of("Spring Boot", "JPA"),
                        List.of("Redis 캐시와 TTL 기반 설계를 먼저 학습"),
                        Instant.parse("2026-04-29T01:00:00Z")
                ));

        mockMvc.perform(get("/api/diagnoses/{diagnosisId}", 30L)
                        .principal(authentication(1L))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.diagnosisId").value("30"))
                .andExpect(jsonPath("$.data.version").value(3))
                .andExpect(jsonPath("$.data.profileId").value("10"))
                .andExpect(jsonPath("$.data.githubAnalysisId").value("20"))
                .andExpect(jsonPath("$.data.targetRole").value("BACKEND_ENGINEER"))
                .andExpect(jsonPath("$.data.currentLevel").value("JUNIOR"))
                .andExpect(jsonPath("$.data.summary").value("Redis 보완 필요"))
                .andExpect(jsonPath("$.data.missingSkills[0].skillName").value("Redis"))
                .andExpect(jsonPath("$.data.missingSkills[0].severity").value("HIGH"))
                .andExpect(jsonPath("$.data.missingSkills[0].reason").value("캐시 설계 경험이 부족함"))
                .andExpect(jsonPath("$.data.missingSkills[0].priorityOrder").value(1))
                .andExpect(jsonPath("$.data.strengths[0]").value("Spring Boot"))
                .andExpect(jsonPath("$.data.recommendations[0]").value("Redis 캐시와 TTL 기반 설계를 먼저 학습"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-04-29T01:00:00Z"))
                .andExpect(jsonPath("$.meta").isMap());

        verify(diagnosisDetailService).findDiagnosis(1L, 30L);
    }

    @Test
    void findDiagnosis_whenDiagnosisDoesNotBelongToUser_returnsNotFound() throws Exception {
        given(diagnosisDetailService.findDiagnosis(1L, 30L))
                .willThrow(new ServiceException(ErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(get("/api/diagnoses/{diagnosisId}", 30L)
                        .principal(authentication(1L))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private Authentication authentication(Long userId) {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(userId), null);
    }
}
