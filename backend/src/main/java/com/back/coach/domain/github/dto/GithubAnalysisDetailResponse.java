package com.back.coach.domain.github.dto;

import java.time.Instant;
import java.util.List;

public record GithubAnalysisDetailResponse(
        String githubAnalysisId,
        Integer version,
        GithubAnalysisPayload.StaticSignals staticSignals,
        List<GithubAnalysisPayload.RepoSummary> repoSummaries,
        List<GithubAnalysisPayload.TechTag> techTags,
        List<GithubAnalysisPayload.DepthEstimate> depthEstimates,
        List<GithubAnalysisPayload.GithubEvidence> evidences,
        List<GithubAnalysisPayload.GithubUserCorrection> userCorrections,
        GithubAnalysisPayload.FinalTechProfile finalTechProfile,
        Instant createdAt
) {

    public static GithubAnalysisDetailResponse from(
            Long githubAnalysisId,
            Integer version,
            GithubAnalysisPayload payload,
            Instant createdAt
    ) {
        return new GithubAnalysisDetailResponse(
                String.valueOf(githubAnalysisId),
                version,
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
