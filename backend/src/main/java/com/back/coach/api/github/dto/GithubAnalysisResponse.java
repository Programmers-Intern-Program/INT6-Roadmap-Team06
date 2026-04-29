package com.back.coach.api.github.dto;

import com.back.coach.domain.github.entity.GithubAnalysis;
import com.back.coach.service.github.AnalysisPayload;
import com.back.coach.service.github.GithubAnalysisService;

import java.time.Instant;
import java.util.List;

// API spec §3.3.1 응답 형태. AnalysisPayload의 모든 필드를 평탄화해 반환.
public record GithubAnalysisResponse(
        Long githubAnalysisId,
        Integer version,
        AnalysisPayload.StaticSignals staticSignals,
        List<AnalysisPayload.RepoSummary> repoSummaries,
        List<AnalysisPayload.TechTag> techTags,
        List<AnalysisPayload.DepthEstimate> depthEstimates,
        List<AnalysisPayload.Evidence> evidences,
        List<AnalysisPayload.UserCorrection> userCorrections,
        AnalysisPayload.FinalTechProfile finalTechProfile,
        Instant createdAt
) {

    public static GithubAnalysisResponse from(GithubAnalysisService.GithubAnalysisResult result) {
        return build(result.id(), result.version(), result.payload(), result.createdAt());
    }

    public static GithubAnalysisResponse from(GithubAnalysis entity, AnalysisPayload payload) {
        return build(entity.getId(), entity.getVersion(), payload, entity.getCreatedAt());
    }

    private static GithubAnalysisResponse build(Long id, Integer version, AnalysisPayload payload, Instant createdAt) {
        return new GithubAnalysisResponse(
                id, version,
                payload.staticSignals(),
                payload.repoSummaries(),
                payload.techTags(),
                payload.depthEstimates(),
                payload.evidences(),
                payload.userCorrections(),
                payload.finalTechProfile(),
                createdAt
        );
    }
}
