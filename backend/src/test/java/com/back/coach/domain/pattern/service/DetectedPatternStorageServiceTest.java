package com.back.coach.domain.pattern.service;

import com.back.coach.domain.pattern.entity.DetectedPattern;
import com.back.coach.domain.pattern.repository.DetectedPatternRepository;
import com.back.coach.global.code.PatternSeverity;
import com.back.coach.global.code.PatternType;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class DetectedPatternStorageServiceTest {

    @Mock
    private DetectedPatternRepository detectedPatternRepository;

    private DetectedPatternStorageService detectedPatternStorageService;

    @BeforeEach
    void setUp() {
        detectedPatternStorageService = new DetectedPatternStorageService(
                detectedPatternRepository,
                new ObjectMapper()
        );
    }

    @Test
    void createPattern_persistsPatternFieldsAndMetadata() {
        given(detectedPatternRepository.findDuplicateCandidate(
                1L,
                PatternType.REPEATED_INCOMPLETE.code(),
                "roadmap_week",
                "12",
                "7"
        )).willReturn(Optional.empty());
        given(detectedPatternRepository.save(any(DetectedPattern.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        DetectedPattern pattern = detectedPatternStorageService.createPattern(
                1L,
                PatternType.REPEATED_INCOMPLETE,
                PatternSeverity.MEDIUM,
                metadata()
        );

        assertThat(pattern.getUserId()).isEqualTo(1L);
        assertThat(pattern.getPatternType()).isEqualTo(PatternType.REPEATED_INCOMPLETE);
        assertThat(pattern.getSeverity()).isEqualTo(PatternSeverity.MEDIUM);
        assertThat(pattern.getMetadata()).contains("\"targetType\": \"roadmap_week\"");
        assertThat(pattern.getProcessedAt()).isNull();
    }

    @Test
    void createPattern_whenDuplicateExists_returnsExistingPatternWithoutSaving() {
        DetectedPattern existing = DetectedPattern.create(
                1L,
                PatternType.REPEATED_INCOMPLETE,
                PatternSeverity.MEDIUM,
                metadata()
        );
        given(detectedPatternRepository.findDuplicateCandidate(
                1L,
                PatternType.REPEATED_INCOMPLETE.code(),
                "roadmap_week",
                "12",
                "7"
        )).willReturn(Optional.of(existing));

        DetectedPattern pattern = detectedPatternStorageService.createPattern(
                1L,
                PatternType.REPEATED_INCOMPLETE,
                PatternSeverity.HIGH,
                metadata()
        );

        assertThat(pattern).isSameAs(existing);
        then(detectedPatternRepository).should(never()).save(any(DetectedPattern.class));
    }

    @Test
    void createPattern_whenMetadataIsNotJsonObject_throwsInvalidInput() {
        assertThatThrownBy(() -> detectedPatternStorageService.createPattern(
                1L,
                PatternType.REPEATED_INCOMPLETE,
                PatternSeverity.MEDIUM,
                "[]"
        )).isInstanceOfSatisfying(ServiceException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));

        then(detectedPatternRepository).shouldHaveNoInteractions();
    }

    @Test
    void createPattern_whenDuplicateKeyFieldMissing_throwsInvalidInput() {
        String metadata = """
                {
                  "count": 3,
                  "targetType": "roadmap_week",
                  "targetId": 12
                }
                """;

        assertThatThrownBy(() -> detectedPatternStorageService.createPattern(
                1L,
                PatternType.REPEATED_INCOMPLETE,
                PatternSeverity.MEDIUM,
                metadata
        )).isInstanceOfSatisfying(ServiceException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));

        then(detectedPatternRepository).shouldHaveNoInteractions();
    }

    @Test
    void markProcessed_setsProcessedAtWhenUnprocessed() {
        DetectedPattern pattern = DetectedPattern.create(
                1L,
                PatternType.REPEATED_INCOMPLETE,
                PatternSeverity.MEDIUM,
                metadata()
        );
        given(detectedPatternRepository.findByIdAndUserId(10L, 1L)).willReturn(Optional.of(pattern));

        DetectedPattern processed = detectedPatternStorageService.markProcessed(1L, 10L);

        assertThat(processed.getProcessedAt()).isNotNull();
    }

    @Test
    void markProcessed_whenAlreadyProcessed_keepsOriginalProcessedAt() {
        DetectedPattern pattern = DetectedPattern.create(
                1L,
                PatternType.REPEATED_INCOMPLETE,
                PatternSeverity.MEDIUM,
                metadata()
        );
        Instant firstProcessedAt = Instant.parse("2026-05-07T00:00:00Z");
        pattern.markProcessed(firstProcessedAt);
        given(detectedPatternRepository.findByIdAndUserId(10L, 1L)).willReturn(Optional.of(pattern));

        DetectedPattern processed = detectedPatternStorageService.markProcessed(1L, 10L);

        assertThat(processed.getProcessedAt()).isEqualTo(firstProcessedAt);
    }

    private String metadata() {
        return """
                {
                  "count": 3,
                  "windowDays": 7,
                  "targetType": "roadmap_week",
                  "targetId": 12
                }
                """;
    }
}
