package com.back.coach.domain.portfolio.repository;

import com.back.coach.domain.portfolio.entity.PortfolioDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortfolioDraftRepository extends JpaRepository<PortfolioDraft, Long> {

    List<PortfolioDraft> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<PortfolioDraft> findByIdAndUserId(Long id, Long userId);
}
