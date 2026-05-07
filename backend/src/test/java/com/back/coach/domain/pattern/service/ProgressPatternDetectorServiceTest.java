package com.back.coach.domain.pattern.service;

import com.back.coach.domain.pattern.entity.DetectedPattern;
import com.back.coach.domain.pattern.repository.ProgressPatternQueryRepository;
import com.back.coach.domain.pattern.repository.ProgressPatternQueryRepository.ConsecutiveDelayCandidate;
import com.back.coach.domain.pattern.repository.ProgressPatternQueryRepository.RepeatedIncompleteCandidate;
import com.back.coach.global.code.PatternSeverity;
import com.back.coach.global.code.PatternType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ProgressPatternDetectorServiceTest {

    @Mock
    private ProgressPatternQueryRepository progressPatternQueryRepository;

    @Mock
    private DetectedPatternStorageService detectedPatternStorageService;

    private ObjectMapper objectMapper;
    private ProgressPatternDetectorService progressPatternDetectorService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        progressPatternDetectorService = new ProgressPatternDetectorService(
                progressPatternQueryRepository,
                detectedPatternStorageService,
                objectMapper
        );
    }

    @Test
    void detectAndStoreStoresRepeatedIncompleteCandidate() throws Exception {
        RepeatedIncompleteCandidate candidate = new RepeatedIncompleteCandidate(
                1L,
                10L,
                20L,
                2,
                3L,
                7,
                Instant.parse("2026-05-07T00:00:00Z")
        );
        DetectedPattern stored = DetectedPattern.create(
                1L,
                PatternType.REPEATED_INCOMPLETE,
                PatternSeverity.MEDIUM,
                "{}"
        );
        given(progressPatternQueryRepository.findRepeatedIncompleteCandidates(eq(1L), any(Instant.class), eq(2), eq(7)))
                .willReturn(List.of(candidate));
        given(progressPatternQueryRepository.findConsecutiveDelayCandidates(1L, 7))
                .willReturn(List.of());
        given(detectedPatternStorageService.createPattern(
                eq(1L),
                eq(PatternType.REPEATED_INCOMPLETE),
                eq(PatternSeverity.MEDIUM),
                any(String.class)
        )).willReturn(stored);

        List<DetectedPattern> detectedPatterns = progressPatternDetectorService.detectAndStore(1L);

        ArgumentCaptor<String> metadataCaptor = ArgumentCaptor.forClass(String.class);
        then(detectedPatternStorageService).should().createPattern(
                eq(1L),
                eq(PatternType.REPEATED_INCOMPLETE),
                eq(PatternSeverity.MEDIUM),
                metadataCaptor.capture()
        );
        JsonNode metadata = objectMapper.readTree(metadataCaptor.getValue());
        assertThat(detectedPatterns).containsExactly(stored);
        assertThat(metadata.path("targetType").asText()).isEqualTo("roadmap_week");
        assertThat(metadata.path("targetId").asLong()).isEqualTo(20L);
        assertThat(metadata.path("windowDays").asInt()).isEqualTo(7);
        assertThat(metadata.path("count").asLong()).isEqualTo(3L);
    }

    @Test
    void detectAndStoreStoresConsecutiveDelayCandidate() throws Exception {
        ConsecutiveDelayCandidate candidate = new ConsecutiveDelayCandidate(
                1L,
                10L,
                21L,
                22L,
                1,
                2,
                7,
                Instant.parse("2026-05-07T00:00:00Z")
        );
        DetectedPattern stored = DetectedPattern.create(
                1L,
                PatternType.CONSECUTIVE_DELAY,
                PatternSeverity.MEDIUM,
                "{}"
        );
        given(progressPatternQueryRepository.findRepeatedIncompleteCandidates(eq(1L), any(Instant.class), eq(2), eq(7)))
                .willReturn(List.of());
        given(progressPatternQueryRepository.findConsecutiveDelayCandidates(1L, 7))
                .willReturn(List.of(candidate));
        given(detectedPatternStorageService.createPattern(
                eq(1L),
                eq(PatternType.CONSECUTIVE_DELAY),
                eq(PatternSeverity.MEDIUM),
                any(String.class)
        )).willReturn(stored);

        List<DetectedPattern> detectedPatterns = progressPatternDetectorService.detectAndStore(1L);

        ArgumentCaptor<String> metadataCaptor = ArgumentCaptor.forClass(String.class);
        then(detectedPatternStorageService).should().createPattern(
                eq(1L),
                eq(PatternType.CONSECUTIVE_DELAY),
                eq(PatternSeverity.MEDIUM),
                metadataCaptor.capture()
        );
        JsonNode metadata = objectMapper.readTree(metadataCaptor.getValue());
        assertThat(detectedPatterns).containsExactly(stored);
        assertThat(metadata.path("targetType").asText()).isEqualTo("roadmap");
        assertThat(metadata.path("targetId").asLong()).isEqualTo(10L);
        assertThat(metadata.path("weekNumbers")).extracting(JsonNode::asInt).containsExactly(1, 2);
        assertThat(metadata.path("roadmapWeekIds")).extracting(JsonNode::asLong).containsExactly(21L, 22L);
    }

    @Test
    void detectAndStoreWhenNoCandidateDoesNotStorePattern() {
        given(progressPatternQueryRepository.findRepeatedIncompleteCandidates(eq(1L), any(Instant.class), eq(2), eq(7)))
                .willReturn(List.of());
        given(progressPatternQueryRepository.findConsecutiveDelayCandidates(1L, 7))
                .willReturn(List.of());

        List<DetectedPattern> detectedPatterns = progressPatternDetectorService.detectAndStore(1L);

        assertThat(detectedPatterns).isEmpty();
        then(detectedPatternStorageService).should(never())
                .createPattern(any(Long.class), any(PatternType.class), any(PatternSeverity.class), any(String.class));
    }
}
