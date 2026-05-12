package com.back.coach.domain.github.service;

import com.back.coach.domain.job.service.JobHistoryEntry;
import com.back.coach.domain.job.service.JobHistoryService;
import com.back.coach.domain.job.service.JobStatusService;
import com.back.coach.domain.job.service.JobStatusSnapshot;
import com.back.coach.global.code.JobStatus;
import com.back.coach.global.config.AsyncExecutorConfig;
import com.back.coach.global.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * `@Async` 자기-호출(self-call)이 Spring AOP proxy를 우회해 동기 실행되는 문제를 막기 위해
 * orchestrator와 worker를 별도 빈으로 분리한다.
 * orchestrator.submit() → worker.runAnalysisAsync() (외부 호출 → proxy 적용 → 진짜 비동기 실행).
 */
@Service
public class GithubAnalysisAsyncWorker {

    private static final Logger log = LoggerFactory.getLogger(GithubAnalysisAsyncWorker.class);

    private static final String STEP_RUNNING = "RUN_ANALYSIS";
    private static final String STEP_DONE = "ANALYSIS_DONE";

    private final GithubAnalysisService analysisService;
    private final JobStatusService jobStatusService;
    private final JobHistoryService jobHistoryService;
    private final String jobType;

    public GithubAnalysisAsyncWorker(
            GithubAnalysisService analysisService,
            JobStatusService jobStatusService,
            JobHistoryService jobHistoryService
    ) {
        this.analysisService = analysisService;
        this.jobStatusService = jobStatusService;
        this.jobHistoryService = jobHistoryService;
        this.jobType = GithubAnalysisAsyncOrchestrator.JOB_TYPE;
    }

    @Async(AsyncExecutorConfig.ANALYSIS_TASK_EXECUTOR)
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
            JobHistoryEntry entry = JobHistoryService.from(jobType, snapshot);
            jobHistoryService.record(userId, entry);
        } catch (Exception e) {
            log.warn("JobHistory record failed userId={} jobId={} reason={}",
                    userId, snapshot.jobId(), e.getMessage());
        }
    }
}
