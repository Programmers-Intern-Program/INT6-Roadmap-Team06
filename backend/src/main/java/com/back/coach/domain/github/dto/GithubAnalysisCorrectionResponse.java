package com.back.coach.domain.github.dto;

import java.time.Instant;

public record GithubAnalysisCorrectionResponse(
        String githubAnalysisId,
        Instant savedAt,
        GithubAnalysisPayload.FinalTechProfile finalTechProfile
) {

    public static GithubAnalysisCorrectionResponse of(
            Long githubAnalysisId,
            Instant savedAt,
            GithubAnalysisPayload.FinalTechProfile finalTechProfile
    ) {
        return new GithubAnalysisCorrectionResponse(
                String.valueOf(githubAnalysisId),
                savedAt,
                finalTechProfile
        );
    }
}
