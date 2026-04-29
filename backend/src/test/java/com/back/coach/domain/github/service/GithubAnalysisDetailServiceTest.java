package com.back.coach.domain.github.service;

import com.back.coach.domain.github.dto.GithubAnalysisCorrectionRequest;
import com.back.coach.domain.github.dto.GithubAnalysisCorrectionResponse;
import com.back.coach.domain.github.dto.GithubAnalysisDetailResponse;
import com.back.coach.domain.github.dto.GithubAnalysisPayload;
import com.back.coach.domain.github.entity.GithubAnalysis;
import com.back.coach.domain.github.repository.GithubAnalysisRepository;
import com.back.coach.global.code.GithubDepthLevel;
import com.back.coach.global.code.GithubEvidenceType;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class GithubAnalysisDetailServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-04-29T02:00:00Z");

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @Mock
    private GithubAnalysisRepository githubAnalysisRepository;

    private GithubAnalysisDetailService githubAnalysisDetailService;

    @BeforeEach
    void setUp() {
        githubAnalysisDetailService = new GithubAnalysisDetailService(
                githubAnalysisRepository,
                objectMapper,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void findAnalysis_whenAnalysisExists_returnsParsedAnalysisDetail() {
        Instant createdAt = Instant.parse("2026-04-29T01:00:00Z");
        GithubAnalysis githubAnalysis = githubAnalysis(validPayload(), createdAt);
        given(githubAnalysisRepository.findByIdAndUserId(40L, 1L)).willReturn(Optional.of(githubAnalysis));

        GithubAnalysisDetailResponse result = githubAnalysisDetailService.findAnalysis(1L, 40L);

        assertThat(result.githubAnalysisId()).isEqualTo("40");
        assertThat(result.version()).isEqualTo(2);
        assertThat(result.staticSignals().primaryLanguages())
                .containsExactly(new GithubAnalysisPayload.PrimaryLanguage("Java", 0.6));
        assertThat(result.staticSignals().activeRepos()).isEqualTo(12);
        assertThat(result.staticSignals().commitFrequency()).isEqualTo("WEEKLY");
        assertThat(result.staticSignals().contributionPattern()).isEqualTo("CONSISTENT");
        assertThat(result.repoSummaries()).containsExactly(new GithubAnalysisPayload.RepoSummary(
                "9001",
                "team06/ai-growth-coach",
                "Spring Boot backend service",
                java.util.List.of("Redis cache", "Batch processing")
        ));
        assertThat(result.techTags()).containsExactly(new GithubAnalysisPayload.TechTag(
                "Redis",
                "Cache configuration and TTL usage were found"
        ));
        assertThat(result.depthEstimates()).containsExactly(new GithubAnalysisPayload.DepthEstimate(
                "Redis",
                GithubDepthLevel.APPLIED,
                "Cache keys and TTL are used beyond dependency setup"
        ));
        assertThat(result.evidences()).containsExactly(new GithubAnalysisPayload.GithubEvidence(
                "team06/ai-growth-coach",
                GithubEvidenceType.CODE,
                "src/main/java/com/back/coach/config/CacheConfig.java",
                "RedisTemplate and TTL configuration"
        ));
        assertThat(result.userCorrections()).containsExactly(new GithubAnalysisPayload.GithubUserCorrection(
                "Redis",
                "Used for cache only, not Pub/Sub"
        ));
        assertThat(result.finalTechProfile().confirmedSkills())
                .containsExactly("Java", "Spring Boot", "Redis");
        assertThat(result.finalTechProfile().focusAreas())
                .containsExactly("Backend", "Performance");
        assertThat(result.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void findAnalysis_whenAnalysisDoesNotBelongToUser_throwsResourceNotFound() {
        given(githubAnalysisRepository.findByIdAndUserId(40L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> githubAnalysisDetailService.findAnalysis(1L, 40L))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void findAnalysis_whenPayloadJsonIsInvalid_throwsInternalServerError() {
        GithubAnalysis githubAnalysis = githubAnalysisWithPayload("not-json");
        given(githubAnalysisRepository.findByIdAndUserId(40L, 1L)).willReturn(Optional.of(githubAnalysis));

        assertThatThrownBy(() -> githubAnalysisDetailService.findAnalysis(1L, 40L))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Test
    void saveCorrections_whenAnalysisExists_replacesCorrectionsAndFinalProfileOnly() throws Exception {
        GithubAnalysis githubAnalysis = githubAnalysisWithPayload(validPayload());
        given(githubAnalysis.getId()).willReturn(40L);
        given(githubAnalysisRepository.findByIdAndUserId(40L, 1L)).willReturn(Optional.of(githubAnalysis));
        GithubAnalysisCorrectionRequest request = new GithubAnalysisCorrectionRequest(
                List.of(new GithubAnalysisPayload.GithubUserCorrection(
                        "Redis",
                        "캐시에만 사용했고 Pub/Sub은 사용하지 않음"
                )),
                new GithubAnalysisPayload.FinalTechProfile(
                        List.of("Java", "Spring Boot", "Redis", "PostgreSQL"),
                        List.of("백엔드", "성능 최적화")
                )
        );

        GithubAnalysisCorrectionResponse result = githubAnalysisDetailService.saveCorrections(1L, 40L, request);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(githubAnalysis).updateAnalysisPayload(payloadCaptor.capture());
        GithubAnalysisPayload updatedPayload = objectMapper.readValue(payloadCaptor.getValue(), GithubAnalysisPayload.class);
        assertThat(updatedPayload.staticSignals().primaryLanguages())
                .containsExactly(new GithubAnalysisPayload.PrimaryLanguage("Java", 0.6));
        assertThat(updatedPayload.repoSummaries()).containsExactly(new GithubAnalysisPayload.RepoSummary(
                "9001",
                "team06/ai-growth-coach",
                "Spring Boot backend service",
                List.of("Redis cache", "Batch processing")
        ));
        assertThat(updatedPayload.techTags()).containsExactly(new GithubAnalysisPayload.TechTag(
                "Redis",
                "Cache configuration and TTL usage were found"
        ));
        assertThat(updatedPayload.depthEstimates()).containsExactly(new GithubAnalysisPayload.DepthEstimate(
                "Redis",
                GithubDepthLevel.APPLIED,
                "Cache keys and TTL are used beyond dependency setup"
        ));
        assertThat(updatedPayload.evidences()).containsExactly(new GithubAnalysisPayload.GithubEvidence(
                "team06/ai-growth-coach",
                GithubEvidenceType.CODE,
                "src/main/java/com/back/coach/config/CacheConfig.java",
                "RedisTemplate and TTL configuration"
        ));
        assertThat(updatedPayload.userCorrections()).containsExactly(new GithubAnalysisPayload.GithubUserCorrection(
                "Redis",
                "캐시에만 사용했고 Pub/Sub은 사용하지 않음"
        ));
        assertThat(updatedPayload.finalTechProfile().confirmedSkills())
                .containsExactly("Java", "Spring Boot", "Redis", "PostgreSQL");
        assertThat(updatedPayload.finalTechProfile().focusAreas())
                .containsExactly("백엔드", "성능 최적화");
        assertThat(result.githubAnalysisId()).isEqualTo("40");
        assertThat(result.savedAt()).isEqualTo(FIXED_NOW);
        assertThat(result.finalTechProfile()).isEqualTo(request.finalTechProfile());
    }

    @Test
    void saveCorrections_whenAnalysisDoesNotBelongToUser_throwsResourceNotFound() {
        GithubAnalysisCorrectionRequest request = new GithubAnalysisCorrectionRequest(
                List.of(),
                new GithubAnalysisPayload.FinalTechProfile(List.of("Java"), List.of("백엔드"))
        );
        given(githubAnalysisRepository.findByIdAndUserId(40L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> githubAnalysisDetailService.saveCorrections(1L, 40L, request))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void saveCorrections_whenPayloadJsonIsInvalid_throwsInternalServerError() {
        GithubAnalysis githubAnalysis = githubAnalysisWithPayload("not-json");
        GithubAnalysisCorrectionRequest request = new GithubAnalysisCorrectionRequest(
                List.of(),
                new GithubAnalysisPayload.FinalTechProfile(List.of("Java"), List.of("백엔드"))
        );
        given(githubAnalysisRepository.findByIdAndUserId(40L, 1L)).willReturn(Optional.of(githubAnalysis));

        assertThatThrownBy(() -> githubAnalysisDetailService.saveCorrections(1L, 40L, request))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private GithubAnalysis githubAnalysis(String payload, Instant createdAt) {
        GithubAnalysis githubAnalysis = githubAnalysisWithPayload(payload);
        given(githubAnalysis.getId()).willReturn(40L);
        given(githubAnalysis.getVersion()).willReturn(2);
        given(githubAnalysis.getCreatedAt()).willReturn(createdAt);
        return githubAnalysis;
    }

    private GithubAnalysis githubAnalysisWithPayload(String payload) {
        GithubAnalysis githubAnalysis = mock(GithubAnalysis.class);
        given(githubAnalysis.getAnalysisPayload()).willReturn(payload);
        return githubAnalysis;
    }

    private String validPayload() {
        return """
                {
                  "staticSignals": {
                    "primaryLanguages": [
                      {
                        "lang": "Java",
                        "ratio": 0.6
                      }
                    ],
                    "activeRepos": 12,
                    "commitFrequency": "WEEKLY",
                    "contributionPattern": "CONSISTENT"
                  },
                  "repoSummaries": [
                    {
                      "repoId": "9001",
                      "repoName": "team06/ai-growth-coach",
                      "summary": "Spring Boot backend service",
                      "highlights": [
                        "Redis cache",
                        "Batch processing"
                      ]
                    }
                  ],
                  "techTags": [
                    {
                      "skillName": "Redis",
                      "tagReason": "Cache configuration and TTL usage were found"
                    }
                  ],
                  "depthEstimates": [
                    {
                      "skillName": "Redis",
                      "level": "APPLIED",
                      "reason": "Cache keys and TTL are used beyond dependency setup"
                    }
                  ],
                  "evidences": [
                    {
                      "repoName": "team06/ai-growth-coach",
                      "type": "CODE",
                      "source": "src/main/java/com/back/coach/config/CacheConfig.java",
                      "summary": "RedisTemplate and TTL configuration"
                    }
                  ],
                  "userCorrections": [
                    {
                      "skillName": "Redis",
                      "correction": "Used for cache only, not Pub/Sub"
                    }
                  ],
                  "finalTechProfile": {
                    "confirmedSkills": [
                      "Java",
                      "Spring Boot",
                      "Redis"
                    ],
                    "focusAreas": [
                      "Backend",
                      "Performance"
                    ]
                  }
                }
                """;
    }
}
