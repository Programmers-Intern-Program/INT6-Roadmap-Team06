package com.back.coach.domain.pattern.service;

import com.back.coach.domain.pattern.entity.PatternSeverity;
import com.back.coach.domain.pattern.entity.PatternType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PatternDetectorService {

    private static final Logger log = LoggerFactory.getLogger(PatternDetectorService.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PatternDetectorService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void runFullScan() {
        log.info("[PatternDetector] scan starting");
        detectRepeatedIncomplete();
        detectConsecutiveDelay();
        detectSkillRepeatedFailure();
        detectGoalDriftCandidateStub();
        log.info("[PatternDetector] scan complete");
    }

    // 7일 내 같은 week에서 비완료 상태(TODO/IN_PROGRESS/SKIPPED) 3회 이상
    void detectRepeatedIncomplete() {
        String sql = """
                SELECT pl.user_id,
                       pl.roadmap_week_id   AS target_id,
                       COUNT(*)             AS cnt,
                       MAX(pl.created_at)   AS last_detected_at
                FROM progress_logs pl
                WHERE pl.status IN ('TODO', 'IN_PROGRESS', 'SKIPPED')
                  AND pl.created_at >= now() - INTERVAL '7 days'
                GROUP BY pl.user_id, pl.roadmap_week_id
                HAVING COUNT(*) >= 3
                """;

        List<Map<String, Object>> rows = jdbc.queryForList(sql);
        for (Map<String, Object> row : rows) {
            long userId = ((Number) row.get("user_id")).longValue();
            long targetId = ((Number) row.get("target_id")).longValue();
            int cnt = ((Number) row.get("cnt")).intValue();
            Instant lastDetectedAt = ((java.sql.Timestamp) row.get("last_detected_at")).toInstant();

            PatternSeverity severity = cnt >= 5 ? PatternSeverity.HIGH : PatternSeverity.MEDIUM;
            String ikey = "roadmap_week:" + targetId;

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("count", cnt);
            meta.put("windowDays", 7);
            meta.put("targetType", "roadmap_week");
            meta.put("targetId", targetId);
            meta.put("lastDetectedAt", lastDetectedAt.toString());

            insertIfAbsent(userId, PatternType.REPEATED_INCOMPLETE, severity, meta, ikey);
        }
    }

    // 14일 내 2개 이상의 week에 걸쳐 TODO/IN_PROGRESS 3회 이상
    void detectConsecutiveDelay() {
        String sql = """
                SELECT pl.user_id,
                       COUNT(*)                        AS cnt,
                       COUNT(DISTINCT pl.roadmap_week_id) AS distinct_weeks,
                       MAX(pl.created_at)              AS last_detected_at
                FROM progress_logs pl
                WHERE pl.status IN ('TODO', 'IN_PROGRESS')
                  AND pl.created_at >= now() - INTERVAL '14 days'
                GROUP BY pl.user_id
                HAVING COUNT(*) >= 3
                   AND COUNT(DISTINCT pl.roadmap_week_id) >= 2
                """;

        List<Map<String, Object>> rows = jdbc.queryForList(sql);
        for (Map<String, Object> row : rows) {
            long userId = ((Number) row.get("user_id")).longValue();
            int cnt = ((Number) row.get("cnt")).intValue();
            Instant lastDetectedAt = ((java.sql.Timestamp) row.get("last_detected_at")).toInstant();

            PatternSeverity severity = cnt >= 6 ? PatternSeverity.MEDIUM : PatternSeverity.LOW;
            String ikey = "user:" + userId;

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("count", cnt);
            meta.put("windowDays", 14);
            meta.put("targetType", "user");
            meta.put("targetId", userId);
            meta.put("lastDetectedAt", lastDetectedAt.toString());

            insertIfAbsent(userId, PatternType.CONSECUTIVE_DELAY, severity, meta, ikey);
        }
    }

    // 14일 내 같은 topic이 SKIPPED 2회 이상
    void detectSkillRepeatedFailure() {
        String sql = """
                SELECT pl.user_id,
                       rw.topic             AS skill,
                       COUNT(*)             AS cnt,
                       MAX(pl.created_at)   AS last_detected_at
                FROM progress_logs pl
                JOIN roadmap_weeks rw ON rw.id = pl.roadmap_week_id
                WHERE pl.status = 'SKIPPED'
                  AND pl.created_at >= now() - INTERVAL '14 days'
                GROUP BY pl.user_id, rw.topic
                HAVING COUNT(*) >= 2
                """;

        List<Map<String, Object>> rows = jdbc.queryForList(sql);
        for (Map<String, Object> row : rows) {
            long userId = ((Number) row.get("user_id")).longValue();
            String skill = (String) row.get("skill");
            int cnt = ((Number) row.get("cnt")).intValue();
            Instant lastDetectedAt = ((java.sql.Timestamp) row.get("last_detected_at")).toInstant();

            PatternSeverity severity = cnt >= 4 ? PatternSeverity.HIGH : PatternSeverity.MEDIUM;
            String ikey = "skill:" + skill.toLowerCase();

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("count", cnt);
            meta.put("windowDays", 14);
            meta.put("targetType", "skill");
            meta.put("skill", skill);
            meta.put("lastDetectedAt", lastDetectedAt.toString());

            insertIfAbsent(userId, PatternType.SKILL_REPEATED_FAILURE, severity, meta, ikey);
        }
    }

    // velocity 비교 데이터가 없어 현재 구현 불가
    void detectGoalDriftCandidateStub() {
        // TODO(#191 follow-up): roadmap_weeks에 예상 완료일 컬럼이 추가되면 구현
        log.debug("[PatternDetector] GOAL_DRIFT_CANDIDATE skipped — schema insufficient");
    }

    private void insertIfAbsent(
            long userId,
            PatternType type,
            PatternSeverity severity,
            Map<String, Object> metadata,
            String idempotencyKey
    ) {
        String metadataJson;
        try {
            metadataJson = objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.error("[PatternDetector] metadata 직렬화 실패 userId={} type={}", userId, type, e);
            return;
        }

        String sql = """
                INSERT INTO detected_patterns (user_id, pattern_type, severity, metadata, idempotency_key)
                SELECT ?, ?, ?, ?::jsonb, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM detected_patterns
                    WHERE user_id = ?
                      AND pattern_type = ?
                      AND idempotency_key = ?
                      AND processed_at IS NULL
                )
                """;

        int inserted = jdbc.update(sql,
                userId, type.name(), severity.name(), metadataJson, idempotencyKey,
                userId, type.name(), idempotencyKey
        );

        if (inserted > 0) {
            log.debug("[PatternDetector] inserted userId={} type={} ikey={}", userId, type, idempotencyKey);
        }
    }
}
