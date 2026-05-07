package com.back.coach.domain.pattern.repository;

import com.back.coach.domain.pattern.entity.DetectedPattern;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DetectedPatternRepository extends JpaRepository<DetectedPattern, Long> {

    List<DetectedPattern> findByUserIdAndProcessedAtIsNullOrderByCreatedAtDesc(Long userId);

    default List<DetectedPattern> findUnprocessedByUserId(Long userId) {
        return findByUserIdAndProcessedAtIsNullOrderByCreatedAtDesc(userId);
    }

    Optional<DetectedPattern> findByIdAndUserId(Long id, Long userId);
}
