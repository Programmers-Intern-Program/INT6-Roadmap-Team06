package com.back.coach.domain.github.repository;

import com.back.coach.domain.github.entity.GithubProject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GithubProjectRepository extends JpaRepository<GithubProject, Long> {

    List<GithubProject> findByUserIdAndGithubConnectionId(Long userId, Long githubConnectionId);

    Optional<GithubProject> findByIdAndUserId(Long id, Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);
}
