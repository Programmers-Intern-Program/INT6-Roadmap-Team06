package com.back.coach.domain.pattern.repository;

import com.back.coach.domain.pattern.entity.DetectedPattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DetectedPatternRepository extends JpaRepository<DetectedPattern, Long> {

    List<DetectedPattern> findByUserIdAndProcessedAtIsNullOrderByCreatedAtDesc(Long userId);

    default List<DetectedPattern> findUnprocessedByUserId(Long userId) {
        return findByUserIdAndProcessedAtIsNullOrderByCreatedAtDesc(userId);
    }

    Optional<DetectedPattern> findByIdAndUserId(Long id, Long userId);

    @Query(value = """
            SELECT *
            FROM detected_patterns
            WHERE user_id = :userId
              AND pattern_type = :patternType
              AND metadata ->> 'targetType' = :targetType
              AND metadata ->> 'targetId' = :targetId
              AND metadata ->> 'windowDays' = :windowDays
            ORDER BY created_at DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<DetectedPattern> findDuplicateCandidate(
            @Param("userId") Long userId,
            @Param("patternType") String patternType,
            @Param("targetType") String targetType,
            @Param("targetId") String targetId,
            @Param("windowDays") String windowDays
    );
}
