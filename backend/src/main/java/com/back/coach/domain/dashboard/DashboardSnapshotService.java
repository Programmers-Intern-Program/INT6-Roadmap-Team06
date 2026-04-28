package com.back.coach.domain.dashboard;

import com.back.coach.domain.diagnosis.entity.CapabilityDiagnosis;
import com.back.coach.domain.diagnosis.repository.CapabilityDiagnosisRepository;
import com.back.coach.domain.github.entity.GithubAnalysis;
import com.back.coach.domain.github.repository.GithubAnalysisRepository;
import com.back.coach.domain.roadmap.RoadmapProgressSnapshot;
import com.back.coach.domain.roadmap.RoadmapProgressSnapshotService;
import com.back.coach.domain.roadmap.entity.LearningRoadmap;
import com.back.coach.domain.roadmap.entity.RoadmapWeek;
import com.back.coach.domain.roadmap.repository.LearningRoadmapRepository;
import com.back.coach.domain.roadmap.repository.RoadmapWeekRepository;
import com.back.coach.domain.user.entity.UserProfile;
import com.back.coach.domain.user.repository.UserProfileRepository;
import com.back.coach.global.code.ProgressStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DashboardSnapshotService {

    private final UserProfileRepository userProfileRepository;
    private final GithubAnalysisRepository githubAnalysisRepository;
    private final CapabilityDiagnosisRepository capabilityDiagnosisRepository;
    private final LearningRoadmapRepository learningRoadmapRepository;
    private final RoadmapWeekRepository roadmapWeekRepository;
    private final RoadmapProgressSnapshotService roadmapProgressSnapshotService;

    public DashboardSnapshotService(
            UserProfileRepository userProfileRepository,
            GithubAnalysisRepository githubAnalysisRepository,
            CapabilityDiagnosisRepository capabilityDiagnosisRepository,
            LearningRoadmapRepository learningRoadmapRepository,
            RoadmapWeekRepository roadmapWeekRepository,
            RoadmapProgressSnapshotService roadmapProgressSnapshotService
    ) {
        this.userProfileRepository = userProfileRepository;
        this.githubAnalysisRepository = githubAnalysisRepository;
        this.capabilityDiagnosisRepository = capabilityDiagnosisRepository;
        this.learningRoadmapRepository = learningRoadmapRepository;
        this.roadmapWeekRepository = roadmapWeekRepository;
        this.roadmapProgressSnapshotService = roadmapProgressSnapshotService;
    }

    public DashboardSnapshot findSnapshot(Long userId) {
        return new DashboardSnapshot(
                userId,
                userProfileRepository.findByUserId(userId)
                        .map(this::toProfileSummary)
                        .orElse(null),
                githubAnalysisRepository.findTopByUserIdOrderByVersionDescCreatedAtDesc(userId)
                        .map(this::toGithubAnalysisSummary)
                        .orElse(null),
                capabilityDiagnosisRepository.findTopByUserIdOrderByVersionDescCreatedAtDesc(userId)
                        .map(this::toDiagnosisSummary)
                        .orElse(null),
                learningRoadmapRepository.findTopByUserIdOrderByVersionDescCreatedAtDesc(userId)
                        .map(roadmap -> toRoadmapSummary(userId, roadmap))
                        .orElse(null)
        );
    }

    private DashboardSnapshot.ProfileSummary toProfileSummary(UserProfile profile) {
        return new DashboardSnapshot.ProfileSummary(
                profile.getId(),
                profile.getJobRoleId(),
                profile.getCurrentLevel(),
                profile.getWeeklyStudyHours(),
                profile.getTargetDate(),
                profile.getUpdatedAt()
        );
    }

    private DashboardSnapshot.GithubAnalysisSummary toGithubAnalysisSummary(GithubAnalysis githubAnalysis) {
        return new DashboardSnapshot.GithubAnalysisSummary(
                githubAnalysis.getId(),
                githubAnalysis.getVersion(),
                githubAnalysis.getSummary(),
                githubAnalysis.getCreatedAt()
        );
    }

    private DashboardSnapshot.DiagnosisSummary toDiagnosisSummary(CapabilityDiagnosis diagnosis) {
        return new DashboardSnapshot.DiagnosisSummary(
                diagnosis.getId(),
                diagnosis.getVersion(),
                diagnosis.getSummary(),
                diagnosis.getCreatedAt()
        );
    }

    private DashboardSnapshot.RoadmapSummary toRoadmapSummary(Long userId, LearningRoadmap roadmap) {
        List<RoadmapWeek> roadmapWeeks = roadmapWeekRepository.findByRoadmapIdOrderByWeekNumberAsc(roadmap.getId());
        List<Long> roadmapWeekIds = roadmapWeeks.stream()
                .map(RoadmapWeek::getId)
                .toList();
        List<RoadmapProgressSnapshot> progressSnapshots = roadmapProgressSnapshotService.findSnapshots(userId, roadmapWeekIds);

        return new DashboardSnapshot.RoadmapSummary(
                roadmap.getId(),
                roadmap.getVersion(),
                roadmap.getTotalWeeks(),
                roadmap.getSummary(),
                roadmap.getCreatedAt(),
                toProgressSummary(progressSnapshots)
        );
    }

    private DashboardSnapshot.ProgressSummary toProgressSummary(List<RoadmapProgressSnapshot> progressSnapshots) {
        int todoWeeks = 0;
        int inProgressWeeks = 0;
        int doneWeeks = 0;
        int skippedWeeks = 0;

        for (RoadmapProgressSnapshot progressSnapshot : progressSnapshots) {
            ProgressStatus progressStatus = progressSnapshot.progressStatus();
            if (progressStatus == ProgressStatus.TODO) {
                todoWeeks++;
            } else if (progressStatus == ProgressStatus.IN_PROGRESS) {
                inProgressWeeks++;
            } else if (progressStatus == ProgressStatus.DONE) {
                doneWeeks++;
            } else if (progressStatus == ProgressStatus.SKIPPED) {
                skippedWeeks++;
            }
        }

        return new DashboardSnapshot.ProgressSummary(
                progressSnapshots.size(),
                todoWeeks,
                inProgressWeeks,
                doneWeeks,
                skippedWeeks
        );
    }
}
