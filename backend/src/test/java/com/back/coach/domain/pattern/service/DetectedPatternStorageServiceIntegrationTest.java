package com.back.coach.domain.pattern.service;

import com.back.coach.domain.pattern.entity.DetectedPattern;
import com.back.coach.domain.pattern.repository.DetectedPatternRepository;
import com.back.coach.domain.user.entity.User;
import com.back.coach.domain.user.repository.UserRepository;
import com.back.coach.global.code.AuthProvider;
import com.back.coach.global.code.PatternSeverity;
import com.back.coach.global.code.PatternType;
import com.back.coach.support.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class DetectedPatternStorageServiceIntegrationTest {

    @Autowired
    private DetectedPatternStorageService detectedPatternStorageService;

    @Autowired
    private DetectedPatternRepository detectedPatternRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("detected_patterns에 패턴을 저장하고 metadata를 보존한다")
    void createPatternPersistsMetadata() throws Exception {
        Long userId = createUser();

        DetectedPattern saved = detectedPatternStorageService.createPattern(
                userId,
                PatternType.REPEATED_INCOMPLETE,
                PatternSeverity.MEDIUM,
                metadata(12)
        );

        DetectedPattern reloaded = detectedPatternRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getUserId()).isEqualTo(userId);
        assertThat(reloaded.getPatternType()).isEqualTo(PatternType.REPEATED_INCOMPLETE);
        assertThat(reloaded.getSeverity()).isEqualTo(PatternSeverity.MEDIUM);
        assertThat(reloaded.getProcessedAt()).isNull();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(objectMapper.readTree(reloaded.getMetadata()).path("targetId").asInt()).isEqualTo(12);
    }

    @Test
    @DisplayName("미처리 패턴은 사용자별로 최신 생성 순서로 조회한다")
    void findUnprocessedPatternsFiltersByUserAndProcessedAt() {
        Long userId = createUser();
        Long otherUserId = createUser();
        Long olderId = insertPattern(userId, null, "2026-05-07T00:00:00Z", 1);
        Long processedId = insertPattern(userId, "2026-05-07T00:20:00Z", "2026-05-07T00:10:00Z", 2);
        Long newerId = insertPattern(userId, null, "2026-05-07T00:30:00Z", 3);
        Long otherUserPatternId = insertPattern(otherUserId, null, "2026-05-07T00:40:00Z", 4);

        List<DetectedPattern> patterns = detectedPatternStorageService.findUnprocessedPatterns(userId);

        assertThat(patterns)
                .extracting(DetectedPattern::getId)
                .containsExactly(newerId, olderId);
        assertThat(List.of(processedId, otherUserPatternId)).doesNotContainNull();
    }

    @Test
    @DisplayName("처리 완료 마킹 후 미처리 목록에서 제외된다")
    void markProcessedRemovesPatternFromUnprocessedCandidates() {
        Long userId = createUser();
        DetectedPattern saved = detectedPatternStorageService.createPattern(
                userId,
                PatternType.CONSECUTIVE_DELAY,
                PatternSeverity.HIGH,
                metadata(21)
        );

        DetectedPattern processed = detectedPatternStorageService.markProcessed(userId, saved.getId());

        assertThat(processed.getProcessedAt()).isNotNull();
        assertThat(detectedPatternRepository.findById(saved.getId()).orElseThrow().getProcessedAt()).isNotNull();
        assertThat(detectedPatternStorageService.findUnprocessedPatterns(userId)).isEmpty();
    }

    private Long createUser() {
        String key = UUID.randomUUID().toString();
        User user = userRepository.save(
                User.signupFromOAuth(AuthProvider.GITHUB, "pattern-" + key, "pattern-" + key + "@example.com")
        );
        return user.getId();
    }

    private Long insertPattern(Long userId, String processedAt, String createdAt, int targetId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO detected_patterns (
                    user_id, pattern_type, severity, metadata, processed_at, created_at
                )
                VALUES (?, 'REPEATED_INCOMPLETE', 'MEDIUM', ?::jsonb, ?::timestamptz, ?::timestamptz)
                RETURNING id
                """, Long.class, userId, metadata(targetId), processedAt, createdAt);
    }

    private String metadata(int targetId) {
        return """
                {
                  "count": 3,
                  "windowDays": 7,
                  "targetType": "roadmap_week",
                  "targetId": %d
                }
                """.formatted(targetId);
    }
}
