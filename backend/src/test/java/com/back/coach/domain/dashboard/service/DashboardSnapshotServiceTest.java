package com.back.coach.domain.dashboard.service;

import com.back.coach.domain.dashboard.dto.DashboardSnapshot;
import com.back.coach.domain.diagnosis.entity.CapabilityDiagnosis;
import com.back.coach.domain.diagnosis.repository.CapabilityDiagnosisRepository;
import com.back.coach.domain.github.dto.GithubAnalysisPayload;
import com.back.coach.domain.github.entity.GithubAnalysis;
import com.back.coach.domain.github.repository.GithubAnalysisRepository;
import com.back.coach.domain.roadmap.dto.RoadmapProgressSnapshot;
import com.back.coach.domain.roadmap.service.RoadmapProgressSnapshotService;
import com.back.coach.domain.roadmap.entity.LearningRoadmap;
import com.back.coach.domain.roadmap.entity.RoadmapWeek;
import com.back.coach.domain.roadmap.repository.LearningRoadmapRepository;
import com.back.coach.domain.roadmap.repository.RoadmapWeekRepository;
import com.back.coach.domain.user.entity.UserProfile;
import com.back.coach.domain.user.repository.UserProfileRepository;
import com.back.coach.global.code.CurrentLevel;
import com.back.coach.global.code.ProgressStatus;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DashboardSnapshotServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private GithubAnalysisRepository githubAnalysisRepository;

    @Mock
    private CapabilityDiagnosisRepository capabilityDiagnosisRepository;

    @Mock
    private LearningRoadmapRepository learningRoadmapRepository;

    @Mock
    private RoadmapWeekRepository roadmapWeekRepository;

    @Mock
    private RoadmapProgressSnapshotService roadmapProgressSnapshotService;

    @Spy
    private ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @InjectMocks
    private DashboardSnapshotService dashboardSnapshotService;

    @Test
    void findSnapshot_whenNoResultExists_returnsUserIdAndNullSummaries() {
        givenNoLatestResults(1L);

        DashboardSnapshot result = dashboardSnapshotService.findSnapshot(1L);

        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.profile()).isNull();
        assertThat(result.githubAnalysis()).isNull();
        assertThat(result.diagnosis()).isNull();
        assertThat(result.roadmap()).isNull();
        verifyNoInteractions(roadmapWeekRepository, roadmapProgressSnapshotService);
    }

    @Test
    void findSnapshot_whenLatestResultsExist_returnsLatestSummaries() {
        Instant profileUpdatedAt = Instant.parse("2026-04-28T01:00:00Z");
        Instant githubAnalysisCreatedAt = Instant.parse("2026-04-28T02:00:00Z");
        Instant diagnosisCreatedAt = Instant.parse("2026-04-28T03:00:00Z");
        Instant roadmapCreatedAt = Instant.parse("2026-04-28T04:00:00Z");
        UserProfile profile = userProfile(10L, profileUpdatedAt);
        GithubAnalysis githubAnalysis = githubAnalysis(20L, 3, "latest analysis", githubAnalysisCreatedAt);
        CapabilityDiagnosis diagnosis = diagnosis(30L, 4, "latest diagnosis", diagnosisCreatedAt);
        LearningRoadmap roadmap = roadmap(40L, 2, 8, "latest roadmap", roadmapCreatedAt);
        given(userProfileRepository.findByUserId(1L)).willReturn(Optional.of(profile));
        given(githubAnalysisRepository.findTopByUserIdOrderByVersionDescCreatedAtDesc(1L))
                .willReturn(Optional.of(githubAnalysis));
        given(capabilityDiagnosisRepository.findTopByUserIdOrderByVersionDescCreatedAtDesc(1L))
                .willReturn(Optional.of(diagnosis));
        given(learningRoadmapRepository.findTopByUserIdOrderByVersionDescCreatedAtDesc(1L))
                .willReturn(Optional.of(roadmap));
        given(roadmapWeekRepository.findByRoadmapIdOrderByWeekNumberAsc(40L)).willReturn(List.of());
        given(roadmapProgressSnapshotService.findSnapshots(1L, List.of())).willReturn(List.of());

        DashboardSnapshot result = dashboardSnapshotService.findSnapshot(1L);

        assertThat(result.profile()).isEqualTo(new DashboardSnapshot.ProfileSummary(
                10L,
                100L,
                CurrentLevel.JUNIOR,
                12,
                LocalDate.of(2026, 12, 31),
                profileUpdatedAt
        ));
        assertThat(result.githubAnalysis()).isEqualTo(new DashboardSnapshot.GithubAnalysisSummary(
                20L,
                3,
                "latest analysis",
                githubAnalysisCreatedAt,
                finalTechProfile(),
                1
        ));
        assertThat(result.diagnosis()).isEqualTo(new DashboardSnapshot.DiagnosisSummary(
                30L,
                4,
                "latest diagnosis",
                diagnosisCreatedAt
        ));
        assertThat(result.roadmap()).isEqualTo(new DashboardSnapshot.RoadmapSummary(
                40L,
                2,
                8,
                "latest roadmap",
                roadmapCreatedAt,
                new DashboardSnapshot.ProgressSummary(0, 0, 0, 0, 0)
        ));
    }

    @Test
    void findSnapshot_whenLatestRoadmapHasProgressSnapshots_calculatesProgressCounts() {
        LearningRoadmap roadmap = roadmap(40L, 2, 4, "latest roadmap", Instant.parse("2026-04-28T04:00:00Z"));
        givenEmptyLatestResultsExceptRoadmap(1L, roadmap);
        List<RoadmapWeek> roadmapWeeks = List.of(
                roadmapWeek(10L),
                roadmapWeek(20L),
                roadmapWeek(30L),
                roadmapWeek(40L)
        );
        given(roadmapWeekRepository.findByRoadmapIdOrderByWeekNumberAsc(40L))
                .willReturn(roadmapWeeks);
        given(roadmapProgressSnapshotService.findSnapshots(1L, List.of(10L, 20L, 30L, 40L)))
                .willReturn(List.of(
                        progressSnapshot(10L, ProgressStatus.TODO),
                        progressSnapshot(20L, ProgressStatus.IN_PROGRESS),
                        progressSnapshot(30L, ProgressStatus.DONE),
                        progressSnapshot(40L, ProgressStatus.SKIPPED)
                ));

        DashboardSnapshot result = dashboardSnapshotService.findSnapshot(1L);

        assertThat(result.roadmap().progress()).isEqualTo(new DashboardSnapshot.ProgressSummary(4, 1, 1, 1, 1));
    }

    @Test
    void findSnapshot_whenLatestRoadmapHasNoWeeks_returnsZeroProgressCounts() {
        LearningRoadmap roadmap = roadmap(40L, 2, 4, "latest roadmap", Instant.parse("2026-04-28T04:00:00Z"));
        givenEmptyLatestResultsExceptRoadmap(1L, roadmap);
        given(roadmapWeekRepository.findByRoadmapIdOrderByWeekNumberAsc(40L)).willReturn(List.of());
        given(roadmapProgressSnapshotService.findSnapshots(1L, List.of())).willReturn(List.of());

        DashboardSnapshot result = dashboardSnapshotService.findSnapshot(1L);

        assertThat(result.roadmap().progress()).isEqualTo(new DashboardSnapshot.ProgressSummary(0, 0, 0, 0, 0));
    }

    @Test
    void findSnapshot_whenGithubAnalysisPayloadIsInvalid_throwsInternalServerError() {
        GithubAnalysis githubAnalysis = mock(GithubAnalysis.class);
        given(githubAnalysis.getAnalysisPayload()).willReturn("{");
        given(userProfileRepository.findByUserId(1L)).willReturn(Optional.empty());
        given(githubAnalysisRepository.findTopByUserIdOrderByVersionDescCreatedAtDesc(1L))
                .willReturn(Optional.of(githubAnalysis));

        assertThatThrownBy(() -> dashboardSnapshotService.findSnapshot(1L))
                .isInstanceOfSatisfying(ServiceException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    private void givenNoLatestResults(Long userId) {
        given(userProfileRepository.findByUserId(userId)).willReturn(Optional.empty());
        given(githubAnalysisRepository.findTopByUserIdOrderByVersionDescCreatedAtDesc(userId))
                .willReturn(Optional.empty());
        given(capabilityDiagnosisRepository.findTopByUserIdOrderByVersionDescCreatedAtDesc(userId))
                .willReturn(Optional.empty());
        given(learningRoadmapRepository.findTopByUserIdOrderByVersionDescCreatedAtDesc(userId))
                .willReturn(Optional.empty());
    }

    private void givenEmptyLatestResultsExceptRoadmap(Long userId, LearningRoadmap roadmap) {
        given(userProfileRepository.findByUserId(userId)).willReturn(Optional.empty());
        given(githubAnalysisRepository.findTopByUserIdOrderByVersionDescCreatedAtDesc(userId))
                .willReturn(Optional.empty());
        given(capabilityDiagnosisRepository.findTopByUserIdOrderByVersionDescCreatedAtDesc(userId))
                .willReturn(Optional.empty());
        given(learningRoadmapRepository.findTopByUserIdOrderByVersionDescCreatedAtDesc(userId))
                .willReturn(Optional.of(roadmap));
    }

    private UserProfile userProfile(Long id, Instant updatedAt) {
        UserProfile userProfile = mock(UserProfile.class);
        given(userProfile.getId()).willReturn(id);
        given(userProfile.getJobRoleId()).willReturn(100L);
        given(userProfile.getCurrentLevel()).willReturn(CurrentLevel.JUNIOR);
        given(userProfile.getWeeklyStudyHours()).willReturn(12);
        given(userProfile.getTargetDate()).willReturn(LocalDate.of(2026, 12, 31));
        given(userProfile.getUpdatedAt()).willReturn(updatedAt);
        return userProfile;
    }

    private GithubAnalysis githubAnalysis(Long id, Integer version, String summary, Instant createdAt) {
        return githubAnalysis(id, version, summary, createdAt, githubAnalysisPayload());
    }

    private GithubAnalysis githubAnalysis(
            Long id,
            Integer version,
            String summary,
            Instant createdAt,
            String analysisPayload
    ) {
        GithubAnalysis githubAnalysis = mock(GithubAnalysis.class);
        given(githubAnalysis.getId()).willReturn(id);
        given(githubAnalysis.getVersion()).willReturn(version);
        given(githubAnalysis.getSummary()).willReturn(summary);
        given(githubAnalysis.getCreatedAt()).willReturn(createdAt);
        given(githubAnalysis.getAnalysisPayload()).willReturn(analysisPayload);
        return githubAnalysis;
    }

    private CapabilityDiagnosis diagnosis(Long id, Integer version, String summary, Instant createdAt) {
        CapabilityDiagnosis diagnosis = mock(CapabilityDiagnosis.class);
        given(diagnosis.getId()).willReturn(id);
        given(diagnosis.getVersion()).willReturn(version);
        given(diagnosis.getSummary()).willReturn(summary);
        given(diagnosis.getCreatedAt()).willReturn(createdAt);
        return diagnosis;
    }

    private LearningRoadmap roadmap(Long id, Integer version, Integer totalWeeks, String summary, Instant createdAt) {
        LearningRoadmap roadmap = mock(LearningRoadmap.class);
        given(roadmap.getId()).willReturn(id);
        given(roadmap.getVersion()).willReturn(version);
        given(roadmap.getTotalWeeks()).willReturn(totalWeeks);
        given(roadmap.getSummary()).willReturn(summary);
        given(roadmap.getCreatedAt()).willReturn(createdAt);
        return roadmap;
    }

    private RoadmapWeek roadmapWeek(Long id) {
        RoadmapWeek roadmapWeek = mock(RoadmapWeek.class);
        given(roadmapWeek.getId()).willReturn(id);
        return roadmapWeek;
    }

    private RoadmapProgressSnapshot progressSnapshot(Long roadmapWeekId, ProgressStatus progressStatus) {
        return new RoadmapProgressSnapshot(roadmapWeekId, progressStatus, null, null);
    }

    private String githubAnalysisPayload() {
        return """
                {
                  "userCorrections": [
                    {
                      "skillName": "Redis",
                      "correction": "캐시에만 사용했고 Pub/Sub은 사용하지 않음"
                    }
                  ],
                  "finalTechProfile": {
                    "confirmedSkills": ["Java", "Spring Boot", "Redis"],
                    "focusAreas": ["백엔드", "성능 최적화"]
                  }
                }
                """;
    }

    private GithubAnalysisPayload.FinalTechProfile finalTechProfile() {
        return new GithubAnalysisPayload.FinalTechProfile(
                List.of("Java", "Spring Boot", "Redis"),
                List.of("백엔드", "성능 최적화")
        );
    }
}
