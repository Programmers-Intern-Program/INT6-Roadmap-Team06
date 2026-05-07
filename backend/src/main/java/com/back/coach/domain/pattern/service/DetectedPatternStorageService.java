package com.back.coach.domain.pattern.service;

import com.back.coach.domain.pattern.entity.DetectedPattern;
import com.back.coach.domain.pattern.repository.DetectedPatternRepository;
import com.back.coach.global.code.PatternSeverity;
import com.back.coach.global.code.PatternType;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DetectedPatternStorageService {

    private final DetectedPatternRepository detectedPatternRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public DetectedPattern createPattern(
            Long userId,
            PatternType patternType,
            PatternSeverity severity,
            String metadata
    ) {
        validateCreateRequest(userId, patternType, severity, metadata);

        DetectedPattern pattern = DetectedPattern.create(userId, patternType, severity, metadata);
        return detectedPatternRepository.save(pattern);
    }

    public List<DetectedPattern> findUnprocessedPatterns(Long userId) {
        validateUserId(userId);
        return detectedPatternRepository.findUnprocessedByUserId(userId);
    }

    @Transactional
    public DetectedPattern markProcessed(Long userId, Long patternId) {
        validateUserId(userId);
        if (patternId == null || patternId < 1) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "patternId는 양수여야 합니다.");
        }

        DetectedPattern pattern = detectedPatternRepository.findByIdAndUserId(patternId, userId)
                .orElseThrow(() -> new ServiceException(ErrorCode.RESOURCE_NOT_FOUND, "감지 패턴을 찾을 수 없습니다."));
        pattern.markProcessed(Instant.now());
        return pattern;
    }

    private void validateCreateRequest(
            Long userId,
            PatternType patternType,
            PatternSeverity severity,
            String metadata
    ) {
        validateUserId(userId);
        if (patternType == null) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "patternType은 필수입니다.");
        }
        if (severity == null) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "severity는 필수입니다.");
        }
        if (metadata == null || metadata.isBlank()) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "metadata는 필수입니다.");
        }
        validateMetadata(metadata);
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId < 1) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "userId는 양수여야 합니다.");
        }
    }

    private void validateMetadata(String metadata) {
        JsonNode root = readMetadata(metadata);
        if (!root.isObject()) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "metadata는 JSON object여야 합니다.");
        }
    }

    private JsonNode readMetadata(String metadata) {
        try {
            return objectMapper.readTree(metadata);
        } catch (JsonProcessingException e) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "metadata는 유효한 JSON이어야 합니다.");
        }
    }
}
