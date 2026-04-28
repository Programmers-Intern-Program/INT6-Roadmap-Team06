package com.back.coach.domain.roadmap;

import com.back.coach.domain.roadmap.entity.ProgressLog;
import com.back.coach.domain.roadmap.repository.LearningRoadmapRepository;
import com.back.coach.domain.roadmap.repository.ProgressLogRepository;
import com.back.coach.domain.roadmap.repository.RoadmapWeekRepository;
import com.back.coach.global.code.ProgressStatus;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import org.springframework.stereotype.Service;

@Service
public class RoadmapProgressCommandService {

    private static final int MAX_NOTE_LENGTH = 1000;

    private final LearningRoadmapRepository learningRoadmapRepository;
    private final RoadmapWeekRepository roadmapWeekRepository;
    private final ProgressLogRepository progressLogRepository;

    public RoadmapProgressCommandService(
            LearningRoadmapRepository learningRoadmapRepository,
            RoadmapWeekRepository roadmapWeekRepository,
            ProgressLogRepository progressLogRepository
    ) {
        this.learningRoadmapRepository = learningRoadmapRepository;
        this.roadmapWeekRepository = roadmapWeekRepository;
        this.progressLogRepository = progressLogRepository;
    }

    public RoadmapProgressCommandResult appendProgress(
            Long userId,
            Long roadmapId,
            Long roadmapWeekId,
            ProgressStatus nextStatus,
            String note
    ) {
        validateNextStatus(nextStatus);
        validateNote(note);
        ensureRoadmapBelongsToUser(userId, roadmapId);
        ensureRoadmapWeekBelongsToRoadmap(roadmapId, roadmapWeekId);
        ProgressTransitionValidator.validate(currentStatus(userId, roadmapWeekId), nextStatus);

        ProgressLog progressLog = ProgressLog.create(userId, roadmapWeekId, nextStatus, note);
        ProgressLog savedProgressLog = progressLogRepository.save(progressLog);

        return new RoadmapProgressCommandResult(
                savedProgressLog.getId(),
                savedProgressLog.getRoadmapWeekId(),
                savedProgressLog.getStatus(),
                savedProgressLog.getCreatedAt()
        );
    }

    private void validateNextStatus(ProgressStatus nextStatus) {
        if (nextStatus == null) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "진도 상태는 필수입니다.");
        }
    }

    private void validateNote(String note) {
        if (note != null && note.length() > MAX_NOTE_LENGTH) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "진도 메모는 1000자를 넘을 수 없습니다.");
        }
    }

    private ProgressStatus currentStatus(Long userId, Long roadmapWeekId) {
        return progressLogRepository.findTopByUserIdAndRoadmapWeekIdOrderByCreatedAtDesc(userId, roadmapWeekId)
                .map(ProgressLog::getStatus)
                .orElse(null);
    }

    private void ensureRoadmapBelongsToUser(Long userId, Long roadmapId) {
        if (learningRoadmapRepository.findByIdAndUserId(roadmapId, userId).isEmpty()) {
            throw new ServiceException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private void ensureRoadmapWeekBelongsToRoadmap(Long roadmapId, Long roadmapWeekId) {
        if (roadmapWeekRepository.findByIdAndRoadmapId(roadmapWeekId, roadmapId).isEmpty()) {
            throw new ServiceException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }
}
