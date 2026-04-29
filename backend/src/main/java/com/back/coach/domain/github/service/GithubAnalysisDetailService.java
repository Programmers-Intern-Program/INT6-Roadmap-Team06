package com.back.coach.domain.github.service;

import com.back.coach.domain.github.dto.GithubAnalysisDetailResponse;
import com.back.coach.domain.github.dto.GithubAnalysisPayload;
import com.back.coach.domain.github.entity.GithubAnalysis;
import com.back.coach.domain.github.repository.GithubAnalysisRepository;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GithubAnalysisDetailService {

    private final GithubAnalysisRepository githubAnalysisRepository;
    private final ObjectMapper objectMapper;

    public GithubAnalysisDetailService(
            GithubAnalysisRepository githubAnalysisRepository,
            ObjectMapper objectMapper
    ) {
        this.githubAnalysisRepository = githubAnalysisRepository;
        this.objectMapper = objectMapper;
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

    private GithubAnalysisPayload parsePayload(String analysisPayload) {
        try {
            return objectMapper.readValue(analysisPayload, GithubAnalysisPayload.class);
        } catch (JsonProcessingException ex) {
            throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
