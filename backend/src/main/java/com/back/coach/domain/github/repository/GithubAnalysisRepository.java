package com.back.coach.domain.github.repository;

import com.back.coach.domain.github.entity.GithubAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GithubAnalysisRepository extends JpaRepository<GithubAnalysis, Long> {

    Optional<GithubAnalysis> findTopByUserIdOrderByVersionDescCreatedAtDesc(Long userId);

    Optional<GithubAnalysis> findByIdAndUserId(Long id, Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    @Query("select max(g.version) from GithubAnalysis g where g.userId = :userId")
    Integer findMaxVersionByUserId(@Param("userId") Long userId);
}
