package com.back.coach.domain.roadmap.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record RoadmapRequest(
        @NotNull Long diagnosisId,
        Long githubAnalysisId,
        Long codingTestAnalysisId,
        @Min(1) @Max(40) Integer weeklyStudyHours,
        @Future LocalDate targetDate
) {
}
