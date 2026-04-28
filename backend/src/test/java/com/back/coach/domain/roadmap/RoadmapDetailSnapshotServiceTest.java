package com.back.coach.domain.roadmap;

import com.back.coach.domain.roadmap.entity.LearningRoadmap;
import com.back.coach.domain.roadmap.entity.RoadmapWeek;
import com.back.coach.domain.roadmap.repository.LearningRoadmapRepository;
import com.back.coach.domain.roadmap.repository.RoadmapWeekRepository;
import com.back.coach.global.code.ProgressStatus;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RoadmapDetailSnapshotServiceTest {

    @Mock
    private LearningRoadmapRepository learningRoadmapRepository;

    @Mock
    private RoadmapWeekRepository roadmapWeekRepository;

    @Mock
    private RoadmapProgressSnapshotService roadmapProgressSnapshotService;

    @InjectMocks
    private RoadmapDetailSnapshotService roadmapDetailSnapshotService;

    @Test
    void findSnapshot_whenRoadmapDoesNotBelongToUser_throwsResourceNotFound() {
        given(learningRoadmapRepository.findByIdAndUserId(10L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> roadmapDetailSnapshotService.findSnapshot(1L, 10L))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        verifyNoInteractions(roadmapWeekRepository, roadmapProgressSnapshotService);
    }

    @Test
    void findSnapshot_whenRoadmapHasNoWeeks_returnsEmptyWeeksWithoutProgressLookup() {
        Instant createdAt = Instant.parse("2026-04-28T01:00:00Z");
        LearningRoadmap roadmap = roadmap(10L, 2, 4, "backend roadmap", createdAt);
        given(learningRoadmapRepository.findByIdAndUserId(10L, 1L)).willReturn(Optional.of(roadmap));
        given(roadmapWeekRepository.findByRoadmapIdOrderByWeekNumberAsc(10L)).willReturn(List.of());

        RoadmapDetailSnapshot result = roadmapDetailSnapshotService.findSnapshot(1L, 10L);

        assertThat(result).isEqualTo(new RoadmapDetailSnapshot(
                10L,
                2,
                4,
                "backend roadmap",
                createdAt,
                List.of()
        ));
        verifyNoInteractions(roadmapProgressSnapshotService);
    }

    @Test
    void findSnapshot_whenRoadmapHasWeeks_returnsWeeksWithProgressInWeekNumberOrder() {
        Instant roadmapCreatedAt = Instant.parse("2026-04-28T01:00:00Z");
        Instant doneAt = Instant.parse("2026-04-28T02:00:00Z");
        LearningRoadmap roadmap = roadmap(10L, 2, 2, "backend roadmap", roadmapCreatedAt);
        RoadmapWeek firstWeek = roadmapWeek(100L, 1, "Redis basics", new BigDecimal("8.0"));
        RoadmapWeek secondWeek = roadmapWeek(200L, 2, "Cache project", new BigDecimal("10.0"));
        given(learningRoadmapRepository.findByIdAndUserId(10L, 1L)).willReturn(Optional.of(roadmap));
        given(roadmapWeekRepository.findByRoadmapIdOrderByWeekNumberAsc(10L))
                .willReturn(List.of(firstWeek, secondWeek));
        given(roadmapProgressSnapshotService.findSnapshots(1L, List.of(100L, 200L)))
                .willReturn(List.of(
                        new RoadmapProgressSnapshot(100L, ProgressStatus.DONE, "완료", doneAt),
                        new RoadmapProgressSnapshot(200L, ProgressStatus.TODO, null, null)
                ));

        RoadmapDetailSnapshot result = roadmapDetailSnapshotService.findSnapshot(1L, 10L);

        assertThat(result.roadmapId()).isEqualTo(10L);
        assertThat(result.weeks()).containsExactly(
                new RoadmapDetailSnapshot.WeekSnapshot(
                        100L,
                        1,
                        "Redis basics",
                        "reason for Redis basics",
                        "[{\"type\":\"READ_DOCS\",\"title\":\"Redis basics task\"}]",
                        "[{\"type\":\"DOCS\",\"title\":\"Redis basics material\"}]",
                        new BigDecimal("8.0"),
                        ProgressStatus.DONE,
                        "완료",
                        doneAt
                ),
                new RoadmapDetailSnapshot.WeekSnapshot(
                        200L,
                        2,
                        "Cache project",
                        "reason for Cache project",
                        "[{\"type\":\"READ_DOCS\",\"title\":\"Cache project task\"}]",
                        "[{\"type\":\"DOCS\",\"title\":\"Cache project material\"}]",
                        new BigDecimal("10.0"),
                        ProgressStatus.TODO,
                        null,
                        null
                )
        );
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

    private RoadmapWeek roadmapWeek(Long id, Integer weekNumber, String topic, BigDecimal estimatedHours) {
        RoadmapWeek roadmapWeek = mock(RoadmapWeek.class);
        given(roadmapWeek.getId()).willReturn(id);
        given(roadmapWeek.getWeekNumber()).willReturn(weekNumber);
        given(roadmapWeek.getTopic()).willReturn(topic);
        given(roadmapWeek.getReasonText()).willReturn("reason for " + topic);
        given(roadmapWeek.getTasksJson()).willReturn("[{\"type\":\"READ_DOCS\",\"title\":\"" + topic + " task\"}]");
        given(roadmapWeek.getMaterialsJson()).willReturn("[{\"type\":\"DOCS\",\"title\":\"" + topic + " material\"}]");
        given(roadmapWeek.getEstimatedHours()).willReturn(estimatedHours);
        return roadmapWeek;
    }
}
