package com.back.coach.domain.roadmap.dto;

import com.back.coach.global.code.ProgressStatus;

import java.time.Instant;

public record RoadmapProgressResponse(
        String progressLogId,
        String roadmapWeekId,
        ProgressStatus status,
        Instant savedAt
) {

    public static RoadmapProgressResponse from(RoadmapProgressCommandResult result) {
        return new RoadmapProgressResponse(
                String.valueOf(result.progressLogId()),
                String.valueOf(result.roadmapWeekId()),
                result.status(),
                result.savedAt()
        );
    }
}
