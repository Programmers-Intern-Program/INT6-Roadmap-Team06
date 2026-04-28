package com.back.coach.domain.roadmap;

import com.back.coach.domain.roadmap.entity.ProgressLog;
import com.back.coach.domain.roadmap.repository.ProgressLogRepository;
import com.back.coach.global.code.ProgressStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RoadmapProgressSnapshotServiceTest {

    @Mock
    private ProgressLogRepository progressLogRepository;

    @InjectMocks
    private RoadmapProgressSnapshotService roadmapProgressSnapshotService;

    @Test
    void findSnapshots_whenNoProgressLog_returnsTodoSnapshot() {
        given(progressLogRepository.findByUserIdAndRoadmapWeekIdInOrderByCreatedAtDesc(1L, List.of(10L)))
                .willReturn(List.of());

        List<RoadmapProgressSnapshot> result = roadmapProgressSnapshotService.findSnapshots(1L, List.of(10L));

        assertThat(result).containsExactly(new RoadmapProgressSnapshot(10L, ProgressStatus.TODO, null, null));
    }

    @Test
    void findSnapshots_whenMultipleLogsForSameWeek_usesLatestLog() {
        Instant latestTime = Instant.parse("2026-04-28T01:00:00Z");
        ProgressLog latestLog = progressLog(10L, ProgressStatus.DONE, "완료", latestTime);
        ProgressLog oldLog = progressLogWithRoadmapWeekId(10L);
        given(progressLogRepository.findByUserIdAndRoadmapWeekIdInOrderByCreatedAtDesc(1L, List.of(10L)))
                .willReturn(List.of(latestLog, oldLog));

        List<RoadmapProgressSnapshot> result = roadmapProgressSnapshotService.findSnapshots(1L, List.of(10L));

        assertThat(result).containsExactly(new RoadmapProgressSnapshot(10L, ProgressStatus.DONE, "완료", latestTime));
    }

    @Test
    void findSnapshots_whenMultipleWeeks_preservesRequestedOrder() {
        Instant skippedAt = Instant.parse("2026-04-28T01:00:00Z");
        Instant doneAt = Instant.parse("2026-04-28T02:00:00Z");
        ProgressLog doneLog = progressLog(20L, ProgressStatus.DONE, "완료", doneAt);
        ProgressLog skippedLog = progressLog(30L, ProgressStatus.SKIPPED, "건너뜀", skippedAt);
        List<Long> roadmapWeekIds = List.of(30L, 10L, 20L);
        given(progressLogRepository.findByUserIdAndRoadmapWeekIdInOrderByCreatedAtDesc(1L, roadmapWeekIds))
                .willReturn(List.of(doneLog, skippedLog));

        List<RoadmapProgressSnapshot> result = roadmapProgressSnapshotService.findSnapshots(1L, roadmapWeekIds);

        assertThat(result).containsExactly(
                new RoadmapProgressSnapshot(30L, ProgressStatus.SKIPPED, "건너뜀", skippedAt),
                new RoadmapProgressSnapshot(10L, ProgressStatus.TODO, null, null),
                new RoadmapProgressSnapshot(20L, ProgressStatus.DONE, "완료", doneAt)
        );
    }

    @Test
    void findSnapshots_whenRoadmapWeekIdsIsEmpty_returnsEmptyListWithoutRepositoryCall() {
        List<RoadmapProgressSnapshot> result = roadmapProgressSnapshotService.findSnapshots(1L, List.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(progressLogRepository);
    }

    private ProgressLog progressLog(Long roadmapWeekId, ProgressStatus status, String note, Instant createdAt) {
        ProgressLog progressLog = mock(ProgressLog.class);
        given(progressLog.getRoadmapWeekId()).willReturn(roadmapWeekId);
        given(progressLog.getStatus()).willReturn(status);
        given(progressLog.getNote()).willReturn(note);
        given(progressLog.getCreatedAt()).willReturn(createdAt);
        return progressLog;
    }

    private ProgressLog progressLogWithRoadmapWeekId(Long roadmapWeekId) {
        ProgressLog progressLog = mock(ProgressLog.class);
        given(progressLog.getRoadmapWeekId()).willReturn(roadmapWeekId);
        return progressLog;
    }
}
