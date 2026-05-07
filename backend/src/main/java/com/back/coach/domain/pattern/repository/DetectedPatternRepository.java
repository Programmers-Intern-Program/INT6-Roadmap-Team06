package com.back.coach.domain.pattern.repository;

import com.back.coach.domain.pattern.entity.DetectedPattern;
import com.back.coach.domain.pattern.entity.PatternType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DetectedPatternRepository extends JpaRepository<DetectedPattern, Long> {

    List<DetectedPattern> findByUserIdAndProcessedAtIsNullOrderByCreatedAtDesc(Long userId);

    List<DetectedPattern> findByUserIdAndPatternTypeAndProcessedAtIsNull(Long userId, PatternType patternType);
}
