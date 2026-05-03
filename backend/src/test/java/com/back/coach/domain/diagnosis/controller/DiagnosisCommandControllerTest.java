package com.back.coach.domain.diagnosis.controller;

import com.back.coach.domain.diagnosis.dto.DiagnosisDetailResponse;
import com.back.coach.domain.diagnosis.dto.DiagnosisRequest;
import com.back.coach.domain.diagnosis.service.DiagnosisCommandService;
import com.back.coach.domain.user.entity.User;
import com.back.coach.domain.user.repository.UserRepository;
import com.back.coach.global.code.AuthProvider;
import com.back.coach.global.code.CurrentLevel;
import com.back.coach.global.code.DiagnosisSeverity;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.back.coach.global.security.JwtTokenProvider;
import com.back.coach.support.ApiTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DiagnosisCommandControllerTest extends ApiTestBase {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DiagnosisCommandService diagnosisCommandService;

    @Test
    void createDiagnosis_whenRequestIsValid_returnsDiagnosisResult() throws Exception {
        User user = userRepository.save(User.signupFromOAuth(AuthProvider.GITHUB, "gh-diagnosis-1", "diagnosis1@example.com"));
        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        given(diagnosisCommandService.createDiagnosis(eq(user.getId()), any(DiagnosisRequest.class)))
                .willReturn(response());

        mockMvc.perform(post("/api/diagnoses")
                        .cookie(new Cookie("accessToken", accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "profileId", 10,
                                "githubAnalysisId", 20
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.diagnosisId").value("30"))
                .andExpect(jsonPath("$.data.version").value(4))
                .andExpect(jsonPath("$.data.profileId").value("10"))
                .andExpect(jsonPath("$.data.githubAnalysisId").value("20"))
                .andExpect(jsonPath("$.data.targetRole").value("BACKEND_DEVELOPER"))
                .andExpect(jsonPath("$.data.missingSkills[0].skillName").value("Redis"))
                .andExpect(jsonPath("$.meta").isMap());
    }

    @Test
    void createDiagnosis_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/diagnoses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileId\":10,\"githubAnalysisId\":20}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createDiagnosis_whenGithubAnalysisIdIsMissing_returnsBadRequest() throws Exception {
        User user = userRepository.save(User.signupFromOAuth(AuthProvider.GITHUB, "gh-diagnosis-2", "diagnosis2@example.com"));
        String accessToken = jwtTokenProvider.createAccessToken(user.getId());

        mockMvc.perform(post("/api/diagnoses")
                        .cookie(new Cookie("accessToken", accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileId\":10}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createDiagnosis_whenProfileOrAnalysisDoesNotExist_returnsNotFound() throws Exception {
        User user = userRepository.save(User.signupFromOAuth(AuthProvider.GITHUB, "gh-diagnosis-3", "diagnosis3@example.com"));
        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        given(diagnosisCommandService.createDiagnosis(eq(user.getId()), any(DiagnosisRequest.class)))
                .willThrow(new ServiceException(ErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(post("/api/diagnoses")
                        .cookie(new Cookie("accessToken", accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileId\":999,\"githubAnalysisId\":20}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private DiagnosisDetailResponse response() {
        return new DiagnosisDetailResponse(
                "30",
                4,
                "10",
                "20",
                "BACKEND_DEVELOPER",
                CurrentLevel.JUNIOR,
                "Redis 보완 필요",
                List.of(new DiagnosisDetailResponse.MissingSkillResponse(
                        "Redis",
                        DiagnosisSeverity.HIGH,
                        "캐시 설계 경험이 부족함",
                        1
                )),
                List.of("Spring Boot"),
                List.of("Redis 캐시와 TTL 기반 설계를 먼저 학습"),
                Instant.parse("2026-04-29T01:00:00Z")
        );
    }
}
