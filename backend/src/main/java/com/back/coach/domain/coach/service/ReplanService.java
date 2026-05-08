package com.back.coach.domain.coach.service;

import com.back.coach.domain.coach.entity.AgentEvent;
import com.back.coach.domain.coach.entity.ReplanProposal;
import com.back.coach.domain.coach.repository.AgentEventRepository;
import com.back.coach.domain.coach.repository.ChatSessionRepository;
import com.back.coach.domain.coach.repository.ReplanProposalRepository;
import com.back.coach.domain.diagnosis.entity.CapabilityDiagnosis;
import com.back.coach.domain.diagnosis.repository.CapabilityDiagnosisRepository;
import com.back.coach.domain.pattern.repository.DetectedPatternRepository;
import com.back.coach.domain.roadmap.dto.RoadmapDetailResponse;
import com.back.coach.domain.roadmap.dto.RoadmapRequest;
import com.back.coach.domain.roadmap.service.RoadmapCommandService;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class ReplanService {

    private final ReplanProposalRepository replanProposalRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final DetectedPatternRepository detectedPatternRepository;
    private final CapabilityDiagnosisRepository diagnosisRepository;
    private final AgentEventRepository agentEventRepository;
    private final RoadmapCommandService roadmapCommandService;

    public ReplanService(
            ReplanProposalRepository replanProposalRepository,
            ChatSessionRepository chatSessionRepository,
            DetectedPatternRepository detectedPatternRepository,
            CapabilityDiagnosisRepository diagnosisRepository,
            AgentEventRepository agentEventRepository,
            RoadmapCommandService roadmapCommandService
    ) {
        this.replanProposalRepository = replanProposalRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.detectedPatternRepository = detectedPatternRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.agentEventRepository = agentEventRepository;
        this.roadmapCommandService = roadmapCommandService;
    }

    public record ReplanResult(String newRoadmapId, Integer newRoadmapVersion) {}

    @Transactional
    public ReplanResult confirm(Long userId, Long sessionId, Long proposalId) {
        ReplanProposal proposal = loadValidProposal(userId, proposalId);

        // 최신 진단으로 새 로드맵 생성
        CapabilityDiagnosis diagnosis = diagnosisRepository
                .findTopByUserIdOrderByVersionDescCreatedAtDesc(userId)
                .orElseThrow(() -> new ServiceException(ErrorCode.RESOURCE_NOT_FOUND,
                        "재계획을 위한 진단 결과가 없습니다."));

        RoadmapRequest roadmapRequest = new RoadmapRequest(
                diagnosis.getId(), null, null, null, null
        );

        RoadmapDetailResponse newRoadmap;
        try {
            newRoadmap = roadmapCommandService.createRoadmap(userId, roadmapRequest);
        } catch (Exception e) {
            throw new ServiceException(ErrorCode.PLANNER_FAILED, e.getMessage());
        }

        Instant now = Instant.now();
        proposal.confirm(now);
        detectedPatternRepository.markAllProcessedByUserId(userId, now);

        // AgentEvent 기록
        agentEventRepository.save(AgentEvent.replanRequested(
                userId, sessionId,
                "{\"proposalId\":" + proposalId + ",\"newRoadmapId\":" + newRoadmap.roadmapId() + "}"
        ));

        return new ReplanResult(newRoadmap.roadmapId(), newRoadmap.version());
    }

    @Transactional
    public void dismiss(Long userId, Long proposalId) {
        ReplanProposal proposal = loadValidProposal(userId, proposalId);
        proposal.dismiss(Instant.now());

        detectedPatternRepository.markAllProcessedByUserId(userId, Instant.now());
    }

    private ReplanProposal loadValidProposal(Long userId, Long proposalId) {
        ReplanProposal proposal = replanProposalRepository.findById(proposalId)
                .orElseThrow(() -> new ServiceException(ErrorCode.PROPOSAL_NOT_FOUND));

        if (!proposal.getUserId().equals(userId)) {
            throw new ServiceException(ErrorCode.FORBIDDEN);
        }

        if (!proposal.isPending() || proposal.isExpired()) {
            throw new ServiceException(ErrorCode.PROPOSAL_EXPIRED);
        }

        return proposal;
    }

}
