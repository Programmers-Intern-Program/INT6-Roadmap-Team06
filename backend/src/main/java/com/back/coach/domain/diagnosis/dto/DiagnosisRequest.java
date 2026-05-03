package com.back.coach.domain.diagnosis.dto;

import jakarta.validation.constraints.NotNull;

public record DiagnosisRequest(
        @NotNull Long profileId,
        @NotNull Long githubAnalysisId
) {
}
