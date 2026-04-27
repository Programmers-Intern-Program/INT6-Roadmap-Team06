package com.back.coach.domain.github.repository;

import com.back.coach.domain.github.entity.GithubProject;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GithubProjectRepository extends JpaRepository<GithubProject, Long> {
}
