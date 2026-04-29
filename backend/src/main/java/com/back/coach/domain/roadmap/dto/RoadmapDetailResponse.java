package com.back.coach.domain.roadmap.dto;

import com.back.coach.global.code.ProgressStatus;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record RoadmapDetailResponse(
        String roadmapId,
        Integer version,
        Integer totalWeeks,
        String summary,
        Instant createdAt,
        List<WeekResponse> weeks
) {

    private static final TypeReference<List<Map<String, Object>>> JSON_ARRAY_TYPE = new TypeReference<>() {
    };

    public static RoadmapDetailResponse from(RoadmapDetailSnapshot snapshot, ObjectMapper objectMapper) {
        return new RoadmapDetailResponse(
                String.valueOf(snapshot.roadmapId()),
                snapshot.version(),
                snapshot.totalWeeks(),
                snapshot.summary(),
                snapshot.createdAt(),
                snapshot.weeks().stream()
                        .map(week -> WeekResponse.from(week, objectMapper))
                        .toList()
        );
    }

    public record WeekResponse(
            String roadmapWeekId,
            Integer weekNumber,
            String topic,
            String reason,
            List<Map<String, Object>> tasks,
            List<Map<String, Object>> materials,
            BigDecimal estimatedHours,
            ProgressStatus progressStatus,
            String progressNote,
            Instant progressUpdatedAt
    ) {

        private static WeekResponse from(RoadmapDetailSnapshot.WeekSnapshot week, ObjectMapper objectMapper) {
            return new WeekResponse(
                    String.valueOf(week.roadmapWeekId()),
                    week.weekNumber(),
                    week.topic(),
                    week.reasonText(),
                    parseJsonArray(week.tasksJson(), objectMapper),
                    parseJsonArray(week.materialsJson(), objectMapper),
                    week.estimatedHours(),
                    week.progressStatus(),
                    week.progressNote(),
                    week.progressUpdatedAt()
            );
        }
    }

    private static List<Map<String, Object>> parseJsonArray(String json, ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(json, JSON_ARRAY_TYPE);
        } catch (JsonProcessingException ex) {
            throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
