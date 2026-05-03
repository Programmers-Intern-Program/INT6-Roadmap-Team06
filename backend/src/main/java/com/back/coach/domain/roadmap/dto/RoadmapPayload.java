package com.back.coach.domain.roadmap.dto;

import com.back.coach.global.code.MaterialType;
import com.back.coach.global.code.RoadmapTaskType;

import java.math.BigDecimal;
import java.util.List;

public record RoadmapPayload(
        List<Week> weeks
) {

    public record Week(
            Integer weekNumber,
            String topic,
            String reason,
            List<Task> tasks,
            List<Material> materials,
            BigDecimal estimatedHours
    ) {
    }

    public record Task(
            RoadmapTaskType type,
            String title
    ) {
    }

    public record Material(
            MaterialType type,
            String title,
            String url
    ) {
    }
}
