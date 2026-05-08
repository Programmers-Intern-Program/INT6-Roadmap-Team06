package com.back.coach.domain.coach.repository;

import com.back.coach.domain.coach.entity.CoachConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CoachConversationRepository extends JpaRepository<CoachConversation, Long> {

    List<CoachConversation> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
}
