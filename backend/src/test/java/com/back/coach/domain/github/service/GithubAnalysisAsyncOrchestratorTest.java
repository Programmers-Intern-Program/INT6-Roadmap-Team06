package com.back.coach.domain.github.service;

import com.back.coach.domain.job.service.JobHistoryService;
import com.back.coach.domain.job.service.JobStatusService;
import com.back.coach.domain.job.service.JobStatusSnapshot;
import com.back.coach.global.code.JobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GithubAnalysisAsyncOrchestratorTest {

    @Mock
    private GithubAnalysisService analysisService;

    @Mock
    private JobStatusService jobStatusService;

    @Mock
    private JobHistoryService jobHistoryService;

    private List<Runnable> submittedTasks;
    private GithubAnalysisAsyncOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        submittedTasks = new ArrayList<>();
        orchestrator = new GithubAnalysisAsyncOrchestrator(
                analysisService,
                jobStatusService,
                jobHistoryService,
                submittedTasks::add
        );
    }

    @Test
    @DisplayName("submit은 분석을 즉시 실행하지 않고 executor에 위임한 뒤 jobId를 반환한다")
    void submitEnqueuesAnalysisTask() {
        Long userId = 10L;
        Long connectionId = 20L;
        List<Long> selectedRepoIds = List.of(1L, 2L);
        List<Long> coreRepoIds = List.of(1L);

        String jobId = orchestrator.submit(userId, connectionId, selectedRepoIds, coreRepoIds);

        assertThat(jobId).isNotBlank();
        assertThat(submittedTasks).hasSize(1);
        verify(jobStatusService).save(userId, jobId, JobStatus.REQUESTED, "REQUEST_ACCEPTED");
        verify(analysisService, never()).run(any(), any(), any(), any());
    }

    @Test
    @DisplayName("executor task가 실행되면 분석 결과를 job success로 저장한다")
    void submittedTaskRunsAnalysisAndSavesSuccess() {
        Long userId = 10L;
        Long connectionId = 20L;
        List<Long> selectedRepoIds = List.of(1L, 2L);
        List<Long> coreRepoIds = List.of(1L);

        when(analysisService.run(userId, connectionId, selectedRepoIds, coreRepoIds))
                .thenReturn(new GithubAnalysisService.GithubAnalysisResult(
                        99L,
                        1,
                        null,
                        "summary",
                        Instant.now(),
                        null
                ));
        when(jobStatusService.saveSuccess(eq(userId), any(), eq("ANALYSIS_DONE"), eq("99")))
                .thenAnswer(invocation -> new JobStatusSnapshot(
                        invocation.getArgument(1),
                        JobStatus.SUCCEEDED,
                        "ANALYSIS_DONE",
                        null,
                        "99"
                ));

        String jobId = orchestrator.submit(userId, connectionId, selectedRepoIds, coreRepoIds);
        submittedTasks.get(0).run();

        verify(jobStatusService).save(userId, jobId, JobStatus.RUNNING, "RUN_ANALYSIS");
        verify(jobStatusService).saveSuccess(userId, jobId, "ANALYSIS_DONE", "99");
        verify(jobHistoryService).record(eq(userId), any());
    }
}
