package com.back.coach.domain.github.repository;

import com.back.coach.domain.github.entity.GithubAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GithubAnalysisRepository extends JpaRepository<GithubAnalysis, Long> {
}
