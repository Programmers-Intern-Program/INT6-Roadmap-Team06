package com.back.coach.domain.github.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record GithubAnalysisCorrectionRequest(
        @NotNull
        List<GithubAnalysisPayload.GithubUserCorrection> userCorrections,

        @NotNull
        GithubAnalysisPayload.FinalTechProfile finalTechProfile
) {
}
