package com.back.coach.domain.roadmap;

import com.back.coach.global.code.ProgressStatus;

import java.time.Instant;

public record RoadmapProgressSnapshot(
        Long roadmapWeekId,
        ProgressStatus progressStatus,
        String progressNote,
        Instant progressUpdatedAt
) {
}
