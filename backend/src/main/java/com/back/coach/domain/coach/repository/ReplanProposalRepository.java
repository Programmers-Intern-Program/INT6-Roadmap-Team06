package com.back.coach.domain.coach.repository;

import com.back.coach.domain.coach.entity.ReplanProposal;
import com.back.coach.global.code.ReplanProposalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReplanProposalRepository extends JpaRepository<ReplanProposal, Long> {

    List<ReplanProposal> findByUserIdAndStatus(Long userId, ReplanProposalStatus status);
}
