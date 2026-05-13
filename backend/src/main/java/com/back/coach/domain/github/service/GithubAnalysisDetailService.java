package com.back.coach.domain.github.service;

import com.back.coach.domain.context.service.ContextSnapshotPublisher;
import com.back.coach.domain.github.dto.GithubAnalysisDetailResponse;
import com.back.coach.domain.github.dto.GithubAnalysisCorrectionRequest;
import com.back.coach.domain.github.dto.GithubAnalysisCorrectionResponse;
import com.back.coach.domain.github.dto.GithubAnalysisPayload;
import com.back.coach.domain.github.dto.GithubAnalysisSummaryResponse;
import com.back.coach.domain.github.entity.GithubAnalysis;
import com.back.coach.domain.github.repository.GithubAnalysisRepository;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class GithubAnalysisDetailService {

    private final GithubAnalysisRepository githubAnalysisRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ContextSnapshotPublisher contextSnapshotPublisher;

    @Autowired
    public GithubAnalysisDetailService(
            GithubAnalysisRepository githubAnalysisRepository,
            ObjectMapper objectMapper,
            ContextSnapshotPublisher contextSnapshotPublisher
    ) {
        this(githubAnalysisRepository, objectMapper, Clock.systemUTC(), contextSnapshotPublisher);
    }

    GithubAnalysisDetailService(
            GithubAnalysisRepository githubAnalysisRepository,
            ObjectMapper objectMapper,
            Clock clock,
            ContextSnapshotPublisher contextSnapshotPublisher
    ) {
        this.githubAnalysisRepository = githubAnalysisRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.contextSnapshotPublisher = contextSnapshotPublisher;
    }

    @Transactional(readOnly = true)
    public List<GithubAnalysisSummaryResponse> listByUser(Long userId) {
        return githubAnalysisRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(analysis -> GithubAnalysisSummaryResponse.from(analysis, extractRepoNames(analysis)))
                .toList();
    }

    private List<String> extractRepoNames(GithubAnalysis analysis) {
        try {
            GithubAnalysisPayload payload = parsePayload(analysis.getAnalysisPayload());
            if (payload.repoSummaries() == null) return List.of();
            return payload.repoSummaries().stream()
                    .map(GithubAnalysisPayload.RepoSummary::repoName)
                    .filter(name -> name != null && !name.isBlank())
                    .distinct()
                    .toList();
        } catch (RuntimeException ex) {
            // payload 파싱 실패 시 list 전체 호출이 깨지지 않도록 빈 목록 반환
            return List.of();
        }
    }

    @Transactional(readOnly = true)
    public GithubAnalysisDetailResponse findAnalysis(Long userId, Long githubAnalysisId) {
        GithubAnalysis githubAnalysis = githubAnalysisRepository.findByIdAndUserId(githubAnalysisId, userId)
                .orElseThrow(() -> new ServiceException(ErrorCode.RESOURCE_NOT_FOUND));
        GithubAnalysisPayload payload = parsePayload(githubAnalysis.getAnalysisPayload());

        return GithubAnalysisDetailResponse.from(
                githubAnalysis.getId(),
                githubAnalysis.getVersion(),
                payload,
                githubAnalysis.getCreatedAt()
        );
    }

    @Transactional
    public GithubAnalysisCorrectionResponse saveCorrections(
            Long userId,
            Long githubAnalysisId,
            GithubAnalysisCorrectionRequest request
    ) {
        GithubAnalysis githubAnalysis = githubAnalysisRepository.findByIdAndUserId(githubAnalysisId, userId)
                .orElseThrow(() -> new ServiceException(ErrorCode.RESOURCE_NOT_FOUND));
        GithubAnalysisPayload payload = parsePayload(githubAnalysis.getAnalysisPayload());
        GithubAnalysisPayload updatedPayload = new GithubAnalysisPayload(
                payload.staticSignals(),
                payload.repoSummaries(),
                payload.techTags(),
                payload.depthEstimates(),
                payload.evidences(),
                request.userCorrections(),
                request.finalTechProfile(),
                payload.analysisTrace()
        );

        githubAnalysis.updateAnalysisPayload(toJson(updatedPayload));
        Instant savedAt = Instant.now(clock);

        contextSnapshotPublisher.publishProfile(userId);

        return GithubAnalysisCorrectionResponse.of(
                githubAnalysis.getId(),
                savedAt,
                request.finalTechProfile()
        );
    }

    private GithubAnalysisPayload parsePayload(String analysisPayload) {
        try {
            return objectMapper.readValue(analysisPayload, GithubAnalysisPayload.class);
        } catch (JsonProcessingException ex) {
            throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private String toJson(GithubAnalysisPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
