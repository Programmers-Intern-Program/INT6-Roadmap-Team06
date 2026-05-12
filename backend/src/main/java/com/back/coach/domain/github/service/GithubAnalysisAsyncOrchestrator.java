package com.back.coach.domain.github.service;

import com.back.coach.domain.job.service.JobHistoryEntry;
import com.back.coach.domain.job.service.JobHistoryService;
import com.back.coach.domain.job.service.JobStatusService;
import com.back.coach.domain.job.service.JobStatusSnapshot;
import com.back.coach.global.code.JobStatus;
import com.back.coach.global.config.AsyncExecutorConfig;
import com.back.coach.global.exception.ServiceException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * GitHub 분석 파이프라인을 비동기 잡으로 감싼다.
 * 호출 흐름:
 *   1. submit() 호출 → jobId 생성, REQUESTED 상태 저장, runAnalysisAsync에 위임
 *   2. runAnalysisAsync() (analysisTaskExecutor 풀에서 실행) → RUNNING 갱신
 *   3. GithubAnalysisService.run() 실행
 *   4. 성공: SUCCEEDED + resultId = analysisId / 실패: FAILED + error 메시지
 *   5. finalized(SUCCEEDED/FAILED) 시 JobHistoryService에 이력 기록
 */
@Service
public class GithubAnalysisAsyncOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(GithubAnalysisAsyncOrchestrator.class);

    private static final String STEP_REQUESTED = "REQUEST_ACCEPTED";
    private static final String STEP_RUNNING = "RUN_ANALYSIS";
    private static final String STEP_DONE = "ANALYSIS_DONE";
    public static final String JOB_TYPE = "GITHUB_ANALYSIS";

    private final GithubAnalysisService analysisService;
    private final JobStatusService jobStatusService;
    private final JobHistoryService jobHistoryService;
    private final Executor analysisTaskExecutor;

    public GithubAnalysisAsyncOrchestrator(
            GithubAnalysisService analysisService,
            JobStatusService jobStatusService,
            JobHistoryService jobHistoryService,
            @Qualifier(AsyncExecutorConfig.ANALYSIS_TASK_EXECUTOR) Executor analysisTaskExecutor
    ) {
        this.analysisService = analysisService;
        this.jobStatusService = jobStatusService;
        this.jobHistoryService = jobHistoryService;
        this.analysisTaskExecutor = analysisTaskExecutor;
    }

    public String submit(Long userId, Long githubConnectionId, List<Long> selectedRepoIds, List<Long> coreRepoIds) {
        String jobId = UUID.randomUUID().toString();
        jobStatusService.save(userId, jobId, JobStatus.REQUESTED, STEP_REQUESTED);
        analysisTaskExecutor.execute(() -> runAnalysisAsync(userId, jobId, githubConnectionId, selectedRepoIds, coreRepoIds));
        return jobId;
    }

    public void runAnalysisAsync(
            Long userId,
            String jobId,
            Long githubConnectionId,
            List<Long> selectedRepoIds,
            List<Long> coreRepoIds
    ) {
        try {
            jobStatusService.save(userId, jobId, JobStatus.RUNNING, STEP_RUNNING);
            GithubAnalysisService.GithubAnalysisResult result =
                    analysisService.run(userId, githubConnectionId, selectedRepoIds, coreRepoIds);
            JobStatusSnapshot snapshot = jobStatusService.saveSuccess(
                    userId, jobId, STEP_DONE, String.valueOf(result.id()));
            recordHistorySafely(userId, snapshot);
            log.debug("Analysis job succeeded jobId={} userId={} analysisId={}", jobId, userId, result.id());
        } catch (ServiceException e) {
            log.warn("Analysis job failed jobId={} userId={} code={} message={}",
                    jobId, userId, e.getErrorCode(), e.getMessage());
            JobStatusSnapshot snapshot = jobStatusService.saveFailure(
                    userId, jobId, STEP_RUNNING, e.getMessage());
            recordHistorySafely(userId, snapshot);
        } catch (Exception e) {
            log.warn("Analysis job failed jobId={} userId={} type={} message={}",
                    jobId, userId, e.getClass().getSimpleName(), e.getMessage());
            JobStatusSnapshot snapshot = jobStatusService.saveFailure(
                    userId, jobId, STEP_RUNNING, "분석 중 예기치 못한 오류가 발생했습니다.");
            recordHistorySafely(userId, snapshot);
        }
    }

    private void recordHistorySafely(Long userId, JobStatusSnapshot snapshot) {
        try {
            JobHistoryEntry entry = JobHistoryService.from(JOB_TYPE, snapshot);
            jobHistoryService.record(userId, entry);
        } catch (Exception e) {
            log.warn("JobHistory record failed userId={} jobId={} reason={}",
                    userId, snapshot.jobId(), e.getMessage());
        }
    }
}
