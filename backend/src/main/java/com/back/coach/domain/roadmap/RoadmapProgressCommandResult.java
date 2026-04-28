package com.back.coach.domain.roadmap;

import com.back.coach.global.code.ProgressStatus;

import java.time.Instant;

public record RoadmapProgressCommandResult(
        Long progressLogId,
        Long roadmapWeekId,
        ProgressStatus status,
        Instant savedAt
) {
}
