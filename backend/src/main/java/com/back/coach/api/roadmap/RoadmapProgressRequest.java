package com.back.coach.api.roadmap;

import com.back.coach.global.code.ProgressStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RoadmapProgressRequest(
        @NotNull Long roadmapWeekId,
        @NotNull ProgressStatus status,
        @Size(max = 1000) String note
) {
}
