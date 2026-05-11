package com.back.coach.domain.coach.repository;

import com.back.coach.domain.coach.entity.ChatSession;
import com.back.coach.global.code.ChatSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    Optional<ChatSession> findFirstByUserIdAndStatusOrderByStartedAtDesc(
            Long userId,
            ChatSessionStatus status
    );

    List<ChatSession> findByUserIdOrderByStartedAtDesc(Long userId);
}
