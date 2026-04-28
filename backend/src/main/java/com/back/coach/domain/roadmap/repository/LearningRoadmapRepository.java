package com.back.coach.domain.roadmap.repository;

import com.back.coach.domain.roadmap.entity.LearningRoadmap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LearningRoadmapRepository extends JpaRepository<LearningRoadmap, Long> {

    Optional<LearningRoadmap> findTopByUserIdOrderByVersionDescCreatedAtDesc(Long userId);

    Optional<LearningRoadmap> findByIdAndUserId(Long id, Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    @Query("select max(l.version) from LearningRoadmap l where l.userId = :userId")
    Integer findMaxVersionByUserId(@Param("userId") Long userId);
}
