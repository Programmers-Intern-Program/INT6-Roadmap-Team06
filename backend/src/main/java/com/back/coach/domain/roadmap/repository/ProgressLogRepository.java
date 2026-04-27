package com.back.coach.domain.roadmap.repository;

import com.back.coach.domain.roadmap.entity.ProgressLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgressLogRepository extends JpaRepository<ProgressLog, Long> {
}
