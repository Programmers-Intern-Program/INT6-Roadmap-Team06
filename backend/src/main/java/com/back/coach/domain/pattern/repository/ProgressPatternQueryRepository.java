package com.back.coach.domain.pattern.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class ProgressPatternQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProgressPatternQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RepeatedIncompleteCandidate> findRepeatedIncompleteCandidates(
            Long userId,
            Instant cutoff,
            int threshold,
            int windowDays
    ) {
        return jdbcTemplate.query("""
                WITH latest_logs AS (
                    SELECT DISTINCT ON (pl.roadmap_week_id)
                           pl.user_id,
                           pl.roadmap_week_id,
                           pl.status,
                           pl.created_at
                    FROM progress_logs pl
                    WHERE pl.user_id = ?
                    ORDER BY pl.roadmap_week_id, pl.created_at DESC, pl.id DESC
                ),
                candidate_counts AS (
                    SELECT pl.user_id,
                           pl.roadmap_week_id,
                           COUNT(*) AS signal_count,
                           MAX(pl.created_at) AS last_detected_at
                    FROM progress_logs pl
                    WHERE pl.user_id = ?
                      AND pl.status IN ('IN_PROGRESS', 'SKIPPED')
                      AND pl.created_at >= ?
                    GROUP BY pl.user_id, pl.roadmap_week_id
                    HAVING COUNT(*) >= ?
                )
                SELECT cc.user_id,
                       rw.roadmap_id,
                       cc.roadmap_week_id,
                       rw.week_number,
                       cc.signal_count,
                       cc.last_detected_at
                FROM candidate_counts cc
                JOIN latest_logs latest
                  ON latest.user_id = cc.user_id
                 AND latest.roadmap_week_id = cc.roadmap_week_id
                 AND latest.status IN ('IN_PROGRESS', 'SKIPPED')
                JOIN roadmap_weeks rw
                  ON rw.id = cc.roadmap_week_id
                JOIN learning_roadmaps lr
                  ON lr.id = rw.roadmap_id
                 AND lr.user_id = cc.user_id
                ORDER BY cc.signal_count DESC, cc.last_detected_at DESC, cc.roadmap_week_id
                """, (rs, rowNum) -> new RepeatedIncompleteCandidate(
                rs.getLong("user_id"),
                rs.getLong("roadmap_id"),
                rs.getLong("roadmap_week_id"),
                rs.getInt("week_number"),
                rs.getLong("signal_count"),
                windowDays,
                rs.getTimestamp("last_detected_at").toInstant()
        ), userId, userId, Timestamp.from(cutoff), threshold);
    }

    public List<ConsecutiveDelayCandidate> findConsecutiveDelayCandidates(Long userId, int windowDays) {
        return jdbcTemplate.query("""
                WITH latest_logs AS (
                    SELECT DISTINCT ON (pl.roadmap_week_id)
                           pl.user_id,
                           pl.roadmap_week_id,
                           pl.status,
                           pl.created_at
                    FROM progress_logs pl
                    WHERE pl.user_id = ?
                    ORDER BY pl.roadmap_week_id, pl.created_at DESC, pl.id DESC
                ),
                stalled_weeks AS (
                    SELECT latest.user_id,
                           rw.roadmap_id,
                           rw.id AS roadmap_week_id,
                           rw.week_number,
                           latest.created_at
                    FROM latest_logs latest
                    JOIN roadmap_weeks rw
                      ON rw.id = latest.roadmap_week_id
                    JOIN learning_roadmaps lr
                      ON lr.id = rw.roadmap_id
                     AND lr.user_id = latest.user_id
                    WHERE latest.status IN ('IN_PROGRESS', 'SKIPPED')
                )
                SELECT first.user_id,
                       first.roadmap_id,
                       first.roadmap_week_id AS first_roadmap_week_id,
                       second.roadmap_week_id AS second_roadmap_week_id,
                       first.week_number AS first_week_number,
                       second.week_number AS second_week_number,
                       GREATEST(first.created_at, second.created_at) AS last_detected_at
                FROM stalled_weeks first
                JOIN stalled_weeks second
                  ON second.user_id = first.user_id
                 AND second.roadmap_id = first.roadmap_id
                 AND second.week_number = first.week_number + 1
                ORDER BY last_detected_at DESC, first.roadmap_id, first.week_number
                """, (rs, rowNum) -> new ConsecutiveDelayCandidate(
                rs.getLong("user_id"),
                rs.getLong("roadmap_id"),
                rs.getLong("first_roadmap_week_id"),
                rs.getLong("second_roadmap_week_id"),
                rs.getInt("first_week_number"),
                rs.getInt("second_week_number"),
                windowDays,
                rs.getTimestamp("last_detected_at").toInstant()
        ), userId);
    }

    public record RepeatedIncompleteCandidate(
            Long userId,
            Long roadmapId,
            Long roadmapWeekId,
            Integer weekNumber,
            Long count,
            Integer windowDays,
            Instant lastDetectedAt
    ) {
    }

    public record ConsecutiveDelayCandidate(
            Long userId,
            Long roadmapId,
            Long firstRoadmapWeekId,
            Long secondRoadmapWeekId,
            Integer firstWeekNumber,
            Integer secondWeekNumber,
            Integer windowDays,
            Instant lastDetectedAt
    ) {
    }
}
