package com.back.coach.domain.roadmap.service;

import com.back.coach.domain.roadmap.dto.RoadmapProgressSnapshot;
import com.back.coach.domain.roadmap.entity.ProgressLog;
import com.back.coach.domain.roadmap.repository.ProgressLogRepository;
import com.back.coach.global.code.ProgressStatus;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RoadmapProgressSnapshotService {

    private final ProgressLogRepository progressLogRepository;

    public RoadmapProgressSnapshotService(ProgressLogRepository progressLogRepository) {
        this.progressLogRepository = progressLogRepository;
    }

    public List<RoadmapProgressSnapshot> findSnapshots(Long userId, List<Long> roadmapWeekIds) {
        if (roadmapWeekIds == null || roadmapWeekIds.isEmpty()) {
            return List.of();
        }

        List<ProgressLog> progressLogs = progressLogRepository
                .findByUserIdAndRoadmapWeekIdInOrderByCreatedAtDesc(userId, roadmapWeekIds);
        Map<Long, ProgressLog> latestByRoadmapWeekId = new HashMap<>();
        for (ProgressLog progressLog : progressLogs) {
            latestByRoadmapWeekId.putIfAbsent(progressLog.getRoadmapWeekId(), progressLog);
        }

        return roadmapWeekIds.stream()
                .map(roadmapWeekId -> toSnapshot(roadmapWeekId, latestByRoadmapWeekId.get(roadmapWeekId)))
                .toList();
    }

    private RoadmapProgressSnapshot toSnapshot(Long roadmapWeekId, ProgressLog progressLog) {
        if (progressLog == null) {
            return new RoadmapProgressSnapshot(roadmapWeekId, ProgressStatus.TODO, null, null);
        }
        return new RoadmapProgressSnapshot(
                roadmapWeekId,
                progressLog.getStatus(),
                progressLog.getNote(),
                progressLog.getCreatedAt()
        );
    }
}
