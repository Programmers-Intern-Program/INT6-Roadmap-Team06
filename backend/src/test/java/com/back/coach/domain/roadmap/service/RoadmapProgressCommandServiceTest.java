package com.back.coach.domain.roadmap.service;

import com.back.coach.domain.roadmap.dto.RoadmapProgressCommandResult;
import com.back.coach.domain.roadmap.entity.LearningRoadmap;
import com.back.coach.domain.roadmap.entity.ProgressLog;
import com.back.coach.domain.roadmap.entity.RoadmapWeek;
import com.back.coach.domain.roadmap.repository.LearningRoadmapRepository;
import com.back.coach.domain.roadmap.repository.ProgressLogRepository;
import com.back.coach.domain.roadmap.repository.RoadmapWeekRepository;
import com.back.coach.global.code.ProgressStatus;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RoadmapProgressCommandServiceTest {

    @Mock
    private LearningRoadmapRepository learningRoadmapRepository;

    @Mock
    private RoadmapWeekRepository roadmapWeekRepository;

    @Mock
    private ProgressLogRepository progressLogRepository;

    @InjectMocks
    private RoadmapProgressCommandService roadmapProgressCommandService;

    @Test
    void appendProgress_whenRoadmapDoesNotBelongToUser_throwsResourceNotFound() {
        given(learningRoadmapRepository.findByIdAndUserId(10L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> roadmapProgressCommandService.appendProgress(
                1L,
                10L,
                100L,
                ProgressStatus.DONE,
                "완료"
        ))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        verifyNoInteractions(roadmapWeekRepository, progressLogRepository);
    }

    @Test
    void appendProgress_whenRoadmapWeekDoesNotBelongToRoadmap_throwsResourceNotFound() {
        givenRoadmapBelongsToUser(1L, 10L);
        given(roadmapWeekRepository.findByIdAndRoadmapId(100L, 10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> roadmapProgressCommandService.appendProgress(
                1L,
                10L,
                100L,
                ProgressStatus.DONE,
                "완료"
        ))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        verifyNoInteractions(progressLogRepository);
    }

    @Test
    void appendProgress_whenInitialStatusToDone_savesNewProgressLogWithCompletedAt() {
        Instant savedAt = Instant.parse("2026-04-28T01:00:00Z");
        ProgressLog savedProgressLog = savedProgressLog(900L, 100L, ProgressStatus.DONE, savedAt);
        givenRoadmapBelongsToUser(1L, 10L);
        givenRoadmapWeekBelongsToRoadmap(10L, 100L);
        given(progressLogRepository.findTopByUserIdAndRoadmapWeekIdOrderByCreatedAtDesc(1L, 100L))
                .willReturn(Optional.empty());
        given(progressLogRepository.save(any(ProgressLog.class)))
                .willReturn(savedProgressLog);

        RoadmapProgressCommandResult result = roadmapProgressCommandService.appendProgress(
                1L,
                10L,
                100L,
                ProgressStatus.DONE,
                "TTL 정리 완료"
        );

        ArgumentCaptor<ProgressLog> captor = ArgumentCaptor.forClass(ProgressLog.class);
        verify(progressLogRepository).save(captor.capture());
        ProgressLog savedArgument = captor.getValue();
        assertThat(savedArgument.getUserId()).isEqualTo(1L);
        assertThat(savedArgument.getRoadmapWeekId()).isEqualTo(100L);
        assertThat(savedArgument.getStatus()).isEqualTo(ProgressStatus.DONE);
        assertThat(savedArgument.getNote()).isEqualTo("TTL 정리 완료");
        assertThat(savedArgument.getCompletedAt()).isNotNull();
        assertThat(result).isEqualTo(new RoadmapProgressCommandResult(
                900L,
                100L,
                ProgressStatus.DONE,
                savedAt
        ));
    }

    @Test
    void appendProgress_whenAllowedTransition_savesNewProgressLogWithoutCompletedAtForNonDone() {
        Instant savedAt = Instant.parse("2026-04-28T01:00:00Z");
        ProgressLog latestProgressLog = latestProgressLog(100L, ProgressStatus.DONE);
        ProgressLog savedProgressLog = savedProgressLog(901L, 100L, ProgressStatus.IN_PROGRESS, savedAt);
        givenRoadmapBelongsToUser(1L, 10L);
        givenRoadmapWeekBelongsToRoadmap(10L, 100L);
        given(progressLogRepository.findTopByUserIdAndRoadmapWeekIdOrderByCreatedAtDesc(1L, 100L))
                .willReturn(Optional.of(latestProgressLog));
        given(progressLogRepository.save(any(ProgressLog.class)))
                .willReturn(savedProgressLog);

        RoadmapProgressCommandResult result = roadmapProgressCommandService.appendProgress(
                1L,
                10L,
                100L,
                ProgressStatus.IN_PROGRESS,
                "다시 보완"
        );

        ArgumentCaptor<ProgressLog> captor = ArgumentCaptor.forClass(ProgressLog.class);
        verify(progressLogRepository).save(captor.capture());
        ProgressLog savedArgument = captor.getValue();
        assertThat(savedArgument.getStatus()).isEqualTo(ProgressStatus.IN_PROGRESS);
        assertThat(savedArgument.getCompletedAt()).isNull();
        assertThat(result).isEqualTo(new RoadmapProgressCommandResult(
                901L,
                100L,
                ProgressStatus.IN_PROGRESS,
                savedAt
        ));
    }

    @Test
    void appendProgress_whenSameStatus_throwsConflict() {
        ProgressLog latestProgressLog = latestProgressLog(100L, ProgressStatus.DONE);
        givenRoadmapBelongsToUser(1L, 10L);
        givenRoadmapWeekBelongsToRoadmap(10L, 100L);
        given(progressLogRepository.findTopByUserIdAndRoadmapWeekIdOrderByCreatedAtDesc(1L, 100L))
                .willReturn(Optional.of(latestProgressLog));

        assertThatThrownBy(() -> roadmapProgressCommandService.appendProgress(
                1L,
                10L,
                100L,
                ProgressStatus.DONE,
                null
        ))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void appendProgress_whenForbiddenTransition_throwsConflict() {
        ProgressLog latestProgressLog = latestProgressLog(100L, ProgressStatus.DONE);
        givenRoadmapBelongsToUser(1L, 10L);
        givenRoadmapWeekBelongsToRoadmap(10L, 100L);
        given(progressLogRepository.findTopByUserIdAndRoadmapWeekIdOrderByCreatedAtDesc(1L, 100L))
                .willReturn(Optional.of(latestProgressLog));

        assertThatThrownBy(() -> roadmapProgressCommandService.appendProgress(
                1L,
                10L,
                100L,
                ProgressStatus.TODO,
                null
        ))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void appendProgress_whenNextStatusIsNull_throwsInvalidInput() {
        assertThatThrownBy(() -> roadmapProgressCommandService.appendProgress(1L, 10L, 100L, null, null))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
        verifyNoInteractions(learningRoadmapRepository, roadmapWeekRepository, progressLogRepository);
    }

    @Test
    void appendProgress_whenNoteIsTooLong_throwsInvalidInput() {
        String tooLongNote = "a".repeat(1001);

        assertThatThrownBy(() -> roadmapProgressCommandService.appendProgress(
                1L,
                10L,
                100L,
                ProgressStatus.DONE,
                tooLongNote
        ))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
        verifyNoInteractions(learningRoadmapRepository, roadmapWeekRepository, progressLogRepository);
    }

    private void givenRoadmapBelongsToUser(Long userId, Long roadmapId) {
        LearningRoadmap roadmap = mock(LearningRoadmap.class);
        given(learningRoadmapRepository.findByIdAndUserId(roadmapId, userId)).willReturn(Optional.of(roadmap));
    }

    private void givenRoadmapWeekBelongsToRoadmap(Long roadmapId, Long roadmapWeekId) {
        RoadmapWeek roadmapWeek = mock(RoadmapWeek.class);
        given(roadmapWeekRepository.findByIdAndRoadmapId(roadmapWeekId, roadmapId)).willReturn(Optional.of(roadmapWeek));
    }

    private ProgressLog latestProgressLog(Long roadmapWeekId, ProgressStatus status) {
        ProgressLog progressLog = mock(ProgressLog.class);
        given(progressLog.getStatus()).willReturn(status);
        return progressLog;
    }

    private ProgressLog savedProgressLog(
            Long progressLogId,
            Long roadmapWeekId,
            ProgressStatus status,
            Instant createdAt
    ) {
        ProgressLog progressLog = mock(ProgressLog.class);
        given(progressLog.getId()).willReturn(progressLogId);
        given(progressLog.getRoadmapWeekId()).willReturn(roadmapWeekId);
        given(progressLog.getStatus()).willReturn(status);
        given(progressLog.getCreatedAt()).willReturn(createdAt);
        return progressLog;
    }
}
