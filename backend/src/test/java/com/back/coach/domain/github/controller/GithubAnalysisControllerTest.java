package com.back.coach.domain.github.controller;

import com.back.coach.domain.github.dto.GithubAnalysisCorrectionRequest;
import com.back.coach.domain.github.dto.GithubAnalysisCorrectionResponse;
import com.back.coach.domain.github.dto.GithubAnalysisDetailResponse;
import com.back.coach.domain.github.dto.GithubAnalysisPayload;
import com.back.coach.domain.github.service.GithubAnalysisDetailService;
import com.back.coach.global.code.GithubDepthLevel;
import com.back.coach.global.code.GithubEvidenceType;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GithubAnalysisControllerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Mock
    private GithubAnalysisDetailService githubAnalysisDetailService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new GithubAnalysisController(githubAnalysisDetailService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void findAnalysis_whenAnalysisExists_returnsGithubAnalysisDetail() throws Exception {
        given(githubAnalysisDetailService.findAnalysis(1L, 40L))
                .willReturn(new GithubAnalysisDetailResponse(
                        "40",
                        2,
                        new GithubAnalysisPayload.StaticSignals(
                                List.of(new GithubAnalysisPayload.PrimaryLanguage("Java", 0.6)),
                                12,
                                "WEEKLY",
                                "CONSISTENT"
                        ),
                        List.of(new GithubAnalysisPayload.RepoSummary(
                                "9001",
                                "team06/ai-growth-coach",
                                "Spring Boot backend service",
                                List.of("Redis cache", "Batch processing")
                        )),
                        List.of(new GithubAnalysisPayload.TechTag(
                                "Redis",
                                "Cache configuration and TTL usage were found"
                        )),
                        List.of(new GithubAnalysisPayload.DepthEstimate(
                                "Redis",
                                GithubDepthLevel.APPLIED,
                                "Cache keys and TTL are used beyond dependency setup"
                        )),
                        List.of(new GithubAnalysisPayload.GithubEvidence(
                                "team06/ai-growth-coach",
                                GithubEvidenceType.CODE,
                                "src/main/java/com/back/coach/config/CacheConfig.java",
                                "RedisTemplate and TTL configuration"
                        )),
                        List.of(new GithubAnalysisPayload.GithubUserCorrection(
                                "Redis",
                                "Used for cache only, not Pub/Sub"
                        )),
                        new GithubAnalysisPayload.FinalTechProfile(
                                List.of("Java", "Spring Boot", "Redis"),
                                List.of("Backend", "Performance")
                        ),
                        Instant.parse("2026-04-29T01:00:00Z")
                ));

        mockMvc.perform(get("/api/github-analyses/{githubAnalysisId}", 40L)
                        .principal(authentication(1L))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.githubAnalysisId").value("40"))
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.staticSignals.primaryLanguages[0].lang").value("Java"))
                .andExpect(jsonPath("$.data.staticSignals.primaryLanguages[0].ratio").value(0.6))
                .andExpect(jsonPath("$.data.staticSignals.activeRepos").value(12))
                .andExpect(jsonPath("$.data.staticSignals.commitFrequency").value("WEEKLY"))
                .andExpect(jsonPath("$.data.staticSignals.contributionPattern").value("CONSISTENT"))
                .andExpect(jsonPath("$.data.repoSummaries[0].repoId").value("9001"))
                .andExpect(jsonPath("$.data.repoSummaries[0].repoName").value("team06/ai-growth-coach"))
                .andExpect(jsonPath("$.data.repoSummaries[0].summary").value("Spring Boot backend service"))
                .andExpect(jsonPath("$.data.repoSummaries[0].highlights[0]").value("Redis cache"))
                .andExpect(jsonPath("$.data.techTags[0].skillName").value("Redis"))
                .andExpect(jsonPath("$.data.depthEstimates[0].level").value("APPLIED"))
                .andExpect(jsonPath("$.data.evidences[0].type").value("CODE"))
                .andExpect(jsonPath("$.data.userCorrections[0].correction").value("Used for cache only, not Pub/Sub"))
                .andExpect(jsonPath("$.data.finalTechProfile.confirmedSkills[0]").value("Java"))
                .andExpect(jsonPath("$.data.finalTechProfile.focusAreas[1]").value("Performance"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-04-29T01:00:00Z"))
                .andExpect(jsonPath("$.meta").isMap());

        verify(githubAnalysisDetailService).findAnalysis(1L, 40L);
    }

    @Test
    void findAnalysis_whenAnalysisDoesNotBelongToUser_returnsNotFound() throws Exception {
        given(githubAnalysisDetailService.findAnalysis(1L, 40L))
                .willThrow(new ServiceException(ErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(get("/api/github-analyses/{githubAnalysisId}", 40L)
                        .principal(authentication(1L))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void saveCorrections_whenValidRequest_returnsSavedCorrectionResponse() throws Exception {
        given(githubAnalysisDetailService.saveCorrections(eq(1L), eq(40L), any(GithubAnalysisCorrectionRequest.class)))
                .willReturn(new GithubAnalysisCorrectionResponse(
                        "40",
                        Instant.parse("2026-04-29T02:00:00Z"),
                        new GithubAnalysisPayload.FinalTechProfile(
                                List.of("Java", "Spring Boot", "Redis"),
                                List.of("백엔드", "성능 최적화")
                        )
                ));

        mockMvc.perform(patch("/api/github-analyses/{githubAnalysisId}/corrections", 40L)
                        .principal(authentication(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userCorrections": [
                                    {
                                      "skillName": "Redis",
                                      "correction": "캐시에만 사용했고 Pub/Sub은 사용하지 않음"
                                    }
                                  ],
                                  "finalTechProfile": {
                                    "confirmedSkills": ["Java", "Spring Boot", "Redis"],
                                    "focusAreas": ["백엔드", "성능 최적화"]
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.githubAnalysisId").value("40"))
                .andExpect(jsonPath("$.data.savedAt").value("2026-04-29T02:00:00Z"))
                .andExpect(jsonPath("$.data.finalTechProfile.confirmedSkills[0]").value("Java"))
                .andExpect(jsonPath("$.data.finalTechProfile.confirmedSkills[2]").value("Redis"))
                .andExpect(jsonPath("$.data.finalTechProfile.focusAreas[1]").value("성능 최적화"))
                .andExpect(jsonPath("$.meta").isMap());

        verify(githubAnalysisDetailService).saveCorrections(eq(1L), eq(40L), any(GithubAnalysisCorrectionRequest.class));
    }

    @Test
    void saveCorrections_whenFinalTechProfileMissing_returnsInvalidInput() throws Exception {
        mockMvc.perform(patch("/api/github-analyses/{githubAnalysisId}/corrections", 40L)
                        .principal(authentication(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userCorrections": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(githubAnalysisDetailService);
    }

    @Test
    void saveCorrections_whenAnalysisDoesNotBelongToUser_returnsNotFound() throws Exception {
        given(githubAnalysisDetailService.saveCorrections(eq(1L), eq(40L), any(GithubAnalysisCorrectionRequest.class)))
                .willThrow(new ServiceException(ErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(patch("/api/github-analyses/{githubAnalysisId}/corrections", 40L)
                        .principal(authentication(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userCorrections": [],
                                  "finalTechProfile": {
                                    "confirmedSkills": ["Java"],
                                    "focusAreas": ["백엔드"]
                                  }
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private Authentication authentication(Long userId) {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(userId), null);
    }
}
