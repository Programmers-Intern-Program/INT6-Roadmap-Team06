package com.back.coach.domain.coach.repository;

import com.back.coach.domain.coach.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
}
