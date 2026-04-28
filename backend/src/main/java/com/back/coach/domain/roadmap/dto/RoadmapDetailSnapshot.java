package com.back.coach.domain.roadmap.dto;

import com.back.coach.global.code.ProgressStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RoadmapDetailSnapshot(
        Long roadmapId,
        Integer version,
        Integer totalWeeks,
        String summary,
        Instant createdAt,
        List<WeekSnapshot> weeks
) {

    public record WeekSnapshot(
            Long roadmapWeekId,
            Integer weekNumber,
            String topic,
            String reasonText,
            String tasksJson,
            String materialsJson,
            BigDecimal estimatedHours,
            ProgressStatus progressStatus,
            String progressNote,
            Instant progressUpdatedAt
    ) {
    }
}
