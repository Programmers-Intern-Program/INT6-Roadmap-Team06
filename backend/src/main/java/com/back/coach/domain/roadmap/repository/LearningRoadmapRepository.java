package com.back.coach.domain.roadmap.repository;

import com.back.coach.domain.roadmap.entity.LearningRoadmap;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningRoadmapRepository extends JpaRepository<LearningRoadmap, Long> {
}
