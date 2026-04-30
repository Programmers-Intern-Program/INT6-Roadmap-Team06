package com.back.coach.domain.github.repository;

import com.back.coach.domain.github.entity.GithubConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GithubConnectionRepository extends JpaRepository<GithubConnection, Long> {

    List<GithubConnection> findByUserIdOrderByConnectedAtDesc(Long userId);

    Optional<GithubConnection> findByIdAndUserId(Long id, Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    Optional<GithubConnection> findByUserIdAndGithubUserId(Long userId, String githubUserId);
}
