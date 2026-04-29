package com.back.coach.domain.github.service;

import com.back.coach.domain.github.dto.GithubAnalysisDetailResponse;
import com.back.coach.domain.github.dto.GithubAnalysisCorrectionRequest;
import com.back.coach.domain.github.dto.GithubAnalysisCorrectionResponse;
import com.back.coach.domain.github.dto.GithubAnalysisPayload;
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

@Service
public class GithubAnalysisDetailService {

    private final GithubAnalysisRepository githubAnalysisRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public GithubAnalysisDetailService(
            GithubAnalysisRepository githubAnalysisRepository,
            ObjectMapper objectMapper
    ) {
        this(githubAnalysisRepository, objectMapper, Clock.systemUTC());
    }

    GithubAnalysisDetailService(
            GithubAnalysisRepository githubAnalysisRepository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.githubAnalysisRepository = githubAnalysisRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
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
                request.finalTechProfile()
        );

        githubAnalysis.updateAnalysisPayload(toJson(updatedPayload));
        Instant savedAt = Instant.now(clock);

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
