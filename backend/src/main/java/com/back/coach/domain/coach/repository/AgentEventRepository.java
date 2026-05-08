package com.back.coach.domain.coach.repository;

import com.back.coach.domain.coach.entity.AgentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentEventRepository extends JpaRepository<AgentEvent, Long> {
}
