package com.back.coach.domain.result.service;

import com.back.coach.domain.diagnosis.repository.CapabilityDiagnosisRepository;
import com.back.coach.domain.github.repository.GithubAnalysisRepository;
import com.back.coach.domain.roadmap.repository.LearningRoadmapRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResultVersionService {

    private final GithubAnalysisRepository githubAnalysisRepository;
    private final CapabilityDiagnosisRepository capabilityDiagnosisRepository;
    private final LearningRoadmapRepository learningRoadmapRepository;

    public int nextGithubAnalysisVersion(Long userId) {
        return nextVersion(githubAnalysisRepository.findMaxVersionByUserId(userId));
    }

    public int nextCapabilityDiagnosisVersion(Long userId) {
        return nextVersion(capabilityDiagnosisRepository.findMaxVersionByUserId(userId));
    }

    public int nextLearningRoadmapVersion(Long userId) {
        return nextVersion(learningRoadmapRepository.findMaxVersionByUserId(userId));
    }

    private int nextVersion(Integer currentMaxVersion) {
        if (currentMaxVersion == null) {
            return 1;
        }
        return currentMaxVersion + 1;
    }
}
