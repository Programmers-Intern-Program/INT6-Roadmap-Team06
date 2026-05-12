package com.back.coach.domain.github.service;

import com.back.coach.domain.job.service.JobStatusService;
import com.back.coach.global.code.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * GitHub 분석 파이프라인을 비동기 잡으로 감싼다.
 *
 *   1. submit() — jobId 생성, REQUESTED 저장, worker로 위임 후 즉시 return
 *   2. worker.runAnalysisAsync() — Spring proxy에 의해 analysisTaskExecutor 풀에서 비동기 실행
 *
 * orchestrator/worker 분리는 self-call → proxy 우회로 동기 실행되는 문제(#321 버그 A)를 막기 위함.
 */
@Service
public class GithubAnalysisAsyncOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(GithubAnalysisAsyncOrchestrator.class);

    private static final String STEP_REQUESTED = "REQUEST_ACCEPTED";
    public static final String JOB_TYPE = "GITHUB_ANALYSIS";

    private final JobStatusService jobStatusService;
    private final GithubAnalysisAsyncWorker worker;

    public GithubAnalysisAsyncOrchestrator(
            JobStatusService jobStatusService,
            GithubAnalysisAsyncWorker worker
    ) {
        this.jobStatusService = jobStatusService;
        this.worker = worker;
    }

    public String submit(Long userId, Long githubConnectionId, List<Long> selectedRepoIds, List<Long> coreRepoIds) {
        String jobId = UUID.randomUUID().toString();
        jobStatusService.save(userId, jobId, JobStatus.REQUESTED, STEP_REQUESTED);
        log.debug("Analysis job submitted jobId={} userId={} repos={}", jobId, userId, selectedRepoIds);
        // 외부 빈 호출 — Spring proxy 통과 → 진짜 @Async 발동
        worker.runAnalysisAsync(userId, jobId, githubConnectionId, selectedRepoIds, coreRepoIds);
        return jobId;
    }
}
