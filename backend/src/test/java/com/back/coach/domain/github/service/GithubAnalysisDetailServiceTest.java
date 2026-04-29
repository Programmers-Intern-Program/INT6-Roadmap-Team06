package com.back.coach.domain.github.service;

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

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class GithubAnalysisDetailServiceTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @Mock
    private GithubAnalysisRepository githubAnalysisRepository;

    private GithubAnalysisDetailService githubAnalysisDetailService;

    @BeforeEach
    void setUp() {
        githubAnalysisDetailService = new GithubAnalysisDetailService(githubAnalysisRepository, objectMapper);
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
