package com.back.coach.domain.roadmap.repository;

import com.back.coach.domain.roadmap.entity.RoadmapWeek;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoadmapWeekRepository extends JpaRepository<RoadmapWeek, Long> {

    List<RoadmapWeek> findByRoadmapIdOrderByWeekNumberAsc(Long roadmapId);

    Optional<RoadmapWeek> findByIdAndRoadmapId(Long id, Long roadmapId);
}
