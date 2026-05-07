package com.back.coach.domain.pattern.service;

import com.back.coach.domain.pattern.entity.DetectedPattern;
import com.back.coach.domain.pattern.repository.ProgressPatternQueryRepository;
import com.back.coach.domain.pattern.repository.ProgressPatternQueryRepository.ConsecutiveDelayCandidate;
import com.back.coach.domain.pattern.repository.ProgressPatternQueryRepository.RepeatedIncompleteCandidate;
import com.back.coach.global.code.PatternSeverity;
import com.back.coach.global.code.PatternType;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class ProgressPatternDetectorService {

    private static final int WINDOW_DAYS = 7;
    private static final int REPEATED_INCOMPLETE_THRESHOLD = 2;

    private final ProgressPatternQueryRepository progressPatternQueryRepository;
    private final DetectedPatternStorageService detectedPatternStorageService;
    private final ObjectMapper objectMapper;

    public ProgressPatternDetectorService(
            ProgressPatternQueryRepository progressPatternQueryRepository,
            DetectedPatternStorageService detectedPatternStorageService,
            ObjectMapper objectMapper
    ) {
        this.progressPatternQueryRepository = progressPatternQueryRepository;
        this.detectedPatternStorageService = detectedPatternStorageService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<DetectedPattern> detectAndStore(Long userId) {
        validateUserId(userId);

        List<DetectedPattern> detectedPatterns = new ArrayList<>();
        Instant cutoff = Instant.now().minus(Duration.ofDays(WINDOW_DAYS));

        for (RepeatedIncompleteCandidate candidate : progressPatternQueryRepository.findRepeatedIncompleteCandidates(
                userId, cutoff, REPEATED_INCOMPLETE_THRESHOLD, WINDOW_DAYS
        )) {
            detectedPatterns.add(detectedPatternStorageService.createPattern(
                    userId,
                    PatternType.REPEATED_INCOMPLETE,
                    PatternSeverity.MEDIUM,
                    repeatedIncompleteMetadata(candidate)
            ));
        }

        for (ConsecutiveDelayCandidate candidate : progressPatternQueryRepository.findConsecutiveDelayCandidates(
                userId, WINDOW_DAYS
        )) {
            detectedPatterns.add(detectedPatternStorageService.createPattern(
                    userId,
                    PatternType.CONSECUTIVE_DELAY,
                    PatternSeverity.MEDIUM,
                    consecutiveDelayMetadata(candidate)
            ));
        }

        return detectedPatterns;
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId < 1) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "userId는 양수여야 합니다.");
        }
    }

    private String repeatedIncompleteMetadata(RepeatedIncompleteCandidate candidate) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("targetType", "roadmap_week");
        metadata.put("targetId", candidate.roadmapWeekId());
        metadata.put("windowDays", candidate.windowDays());
        metadata.put("count", candidate.count());
        metadata.put("roadmapId", candidate.roadmapId());
        metadata.put("weekNumber", candidate.weekNumber());
        metadata.put("lastDetectedAt", candidate.lastDetectedAt().toString());
        return toJson(metadata);
    }

    private String consecutiveDelayMetadata(ConsecutiveDelayCandidate candidate) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("targetType", "roadmap");
        metadata.put("targetId", candidate.roadmapId());
        metadata.put("windowDays", candidate.windowDays());
        metadata.put("count", 2);
        metadata.put("weekNumbers", List.of(candidate.firstWeekNumber(), candidate.secondWeekNumber()));
        metadata.put("roadmapWeekIds", List.of(candidate.firstRoadmapWeekId(), candidate.secondRoadmapWeekId()));
        metadata.put("lastDetectedAt", candidate.lastDetectedAt().toString());
        return toJson(metadata);
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
