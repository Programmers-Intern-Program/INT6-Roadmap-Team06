package com.back.coach.domain.roadmap.dto;

import com.back.coach.domain.roadmap.entity.LearningRoadmap;

import java.time.Instant;

public record RoadmapSummaryResponse(
        String roadmapId,
        Integer version,
        Integer totalWeeks,
        String summary,
        String diagnosisId,
        Instant createdAt
) {
    public static RoadmapSummaryResponse from(LearningRoadmap roadmap) {
        return new RoadmapSummaryResponse(
                String.valueOf(roadmap.getId()),
                roadmap.getVersion(),
                roadmap.getTotalWeeks(),
                roadmap.getSummary(),
                String.valueOf(roadmap.getDiagnosisId()),
                roadmap.getCreatedAt() == null ? Instant.now() : roadmap.getCreatedAt()
        );
    }
}
