package com.back.coach.domain.roadmap.repository;

import com.back.coach.domain.roadmap.entity.ProgressLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProgressLogRepository extends JpaRepository<ProgressLog, Long> {

    List<ProgressLog> findByUserIdAndRoadmapWeekIdOrderByCreatedAtDesc(Long userId, Long roadmapWeekId);

    Optional<ProgressLog> findTopByUserIdAndRoadmapWeekIdOrderByCreatedAtDesc(Long userId, Long roadmapWeekId);

    boolean existsByIdAndUserId(Long id, Long userId);
}
