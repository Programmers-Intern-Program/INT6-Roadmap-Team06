package com.back.coach.domain.result.service;

import com.back.coach.domain.diagnosis.repository.CapabilityDiagnosisRepository;
import com.back.coach.domain.github.repository.GithubAnalysisRepository;
import com.back.coach.domain.roadmap.repository.LearningRoadmapRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ResultVersionServiceTest {

    @Mock
    private GithubAnalysisRepository githubAnalysisRepository;

    @Mock
    private CapabilityDiagnosisRepository capabilityDiagnosisRepository;

    @Mock
    private LearningRoadmapRepository learningRoadmapRepository;

    @InjectMocks
    private ResultVersionService resultVersionService;

    @Test
    void nextGithubAnalysisVersion_whenNoPreviousResult_returnsOne() {
        given(githubAnalysisRepository.findMaxVersionByUserId(1L)).willReturn(null);

        int nextVersion = resultVersionService.nextGithubAnalysisVersion(1L);

        assertThat(nextVersion).isEqualTo(1);
    }

    @Test
    void nextGithubAnalysisVersion_whenPreviousResultExists_returnsNextVersion() {
        given(githubAnalysisRepository.findMaxVersionByUserId(1L)).willReturn(5);

        int nextVersion = resultVersionService.nextGithubAnalysisVersion(1L);

        assertThat(nextVersion).isEqualTo(6);
    }

    @Test
    void nextCapabilityDiagnosisVersion_whenNoPreviousResult_returnsOne() {
        given(capabilityDiagnosisRepository.findMaxVersionByUserId(1L)).willReturn(null);

        int nextVersion = resultVersionService.nextCapabilityDiagnosisVersion(1L);

        assertThat(nextVersion).isEqualTo(1);
    }

    @Test
    void nextCapabilityDiagnosisVersion_whenPreviousResultExists_returnsNextVersion() {
        given(capabilityDiagnosisRepository.findMaxVersionByUserId(1L)).willReturn(3);

        int nextVersion = resultVersionService.nextCapabilityDiagnosisVersion(1L);

        assertThat(nextVersion).isEqualTo(4);
    }

    @Test
    void nextLearningRoadmapVersion_whenNoPreviousResult_returnsOne() {
        given(learningRoadmapRepository.findMaxVersionByUserId(1L)).willReturn(null);

        int nextVersion = resultVersionService.nextLearningRoadmapVersion(1L);

        assertThat(nextVersion).isEqualTo(1);
    }

    @Test
    void nextLearningRoadmapVersion_whenPreviousResultExists_returnsNextVersion() {
        given(learningRoadmapRepository.findMaxVersionByUserId(1L)).willReturn(2);

        int nextVersion = resultVersionService.nextLearningRoadmapVersion(1L);

        assertThat(nextVersion).isEqualTo(3);
    }
}
