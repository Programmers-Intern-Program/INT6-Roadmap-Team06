package com.back.coach.domain.roadmap;

import com.back.coach.domain.roadmap.entity.LearningRoadmap;
import com.back.coach.domain.roadmap.entity.RoadmapWeek;
import com.back.coach.domain.roadmap.repository.LearningRoadmapRepository;
import com.back.coach.domain.roadmap.repository.RoadmapWeekRepository;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RoadmapDetailSnapshotService {

    private final LearningRoadmapRepository learningRoadmapRepository;
    private final RoadmapWeekRepository roadmapWeekRepository;
    private final RoadmapProgressSnapshotService roadmapProgressSnapshotService;

    public RoadmapDetailSnapshotService(
            LearningRoadmapRepository learningRoadmapRepository,
            RoadmapWeekRepository roadmapWeekRepository,
            RoadmapProgressSnapshotService roadmapProgressSnapshotService
    ) {
        this.learningRoadmapRepository = learningRoadmapRepository;
        this.roadmapWeekRepository = roadmapWeekRepository;
        this.roadmapProgressSnapshotService = roadmapProgressSnapshotService;
    }

    public RoadmapDetailSnapshot findSnapshot(Long userId, Long roadmapId) {
        LearningRoadmap roadmap = learningRoadmapRepository.findByIdAndUserId(roadmapId, userId)
                .orElseThrow(() -> new ServiceException(ErrorCode.RESOURCE_NOT_FOUND));
        List<RoadmapWeek> roadmapWeeks = roadmapWeekRepository.findByRoadmapIdOrderByWeekNumberAsc(roadmapId);

        return new RoadmapDetailSnapshot(
                roadmap.getId(),
                roadmap.getVersion(),
                roadmap.getTotalWeeks(),
                roadmap.getSummary(),
                roadmap.getCreatedAt(),
                toWeekSnapshots(userId, roadmapWeeks)
        );
    }

    private List<RoadmapDetailSnapshot.WeekSnapshot> toWeekSnapshots(Long userId, List<RoadmapWeek> roadmapWeeks) {
        if (roadmapWeeks.isEmpty()) {
            return List.of();
        }

        List<Long> roadmapWeekIds = roadmapWeeks.stream()
                .map(RoadmapWeek::getId)
                .toList();
        Map<Long, RoadmapProgressSnapshot> progressSnapshotByRoadmapWeekId = toProgressSnapshotMap(
                roadmapProgressSnapshotService.findSnapshots(userId, roadmapWeekIds)
        );

        return roadmapWeeks.stream()
                .map(roadmapWeek -> toWeekSnapshot(roadmapWeek, progressSnapshotByRoadmapWeekId.get(roadmapWeek.getId())))
                .toList();
    }

    private Map<Long, RoadmapProgressSnapshot> toProgressSnapshotMap(List<RoadmapProgressSnapshot> progressSnapshots) {
        Map<Long, RoadmapProgressSnapshot> progressSnapshotByRoadmapWeekId = new HashMap<>();
        for (RoadmapProgressSnapshot progressSnapshot : progressSnapshots) {
            progressSnapshotByRoadmapWeekId.put(progressSnapshot.roadmapWeekId(), progressSnapshot);
        }
        return progressSnapshotByRoadmapWeekId;
    }

    private RoadmapDetailSnapshot.WeekSnapshot toWeekSnapshot(
            RoadmapWeek roadmapWeek,
            RoadmapProgressSnapshot progressSnapshot
    ) {
        return new RoadmapDetailSnapshot.WeekSnapshot(
                roadmapWeek.getId(),
                roadmapWeek.getWeekNumber(),
                roadmapWeek.getTopic(),
                roadmapWeek.getReasonText(),
                roadmapWeek.getTasksJson(),
                roadmapWeek.getMaterialsJson(),
                roadmapWeek.getEstimatedHours(),
                progressSnapshot.progressStatus(),
                progressSnapshot.progressNote(),
                progressSnapshot.progressUpdatedAt()
        );
    }
}
