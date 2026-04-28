package com.back.coach.domain.github.repository;

import com.back.coach.domain.github.entity.GithubConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GithubConnectionRepository extends JpaRepository<GithubConnection, Long> {

    Optional<GithubConnection> findByUserIdAndGithubUserId(Long userId, String githubUserId);
}
