package com.back.coach.domain.github.service;

import com.back.coach.domain.job.service.JobStatusService;
import com.back.coach.domain.job.service.JobStatusSnapshot;
import com.back.coach.global.code.JobStatus;
import com.back.coach.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

/**
 * orchestrator/worker 분리가 실제로 비동기로 동작하는지 검증.
 * (Self-call 버그였다면 submit() 호출이 worker.run을 block했을 것이다.)
 */
@IntegrationTest
class GithubAnalysisAsyncOrchestratorIntegrationTest {

    private static final Long USER_ID = 9_999_002L;

    @Autowired
    private GithubAnalysisAsyncOrchestrator orchestrator;

    @Autowired
    private JobStatusService jobStatusService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private GithubAnalysisService analysisService;

    @AfterEach
    void cleanUp() {
        redisTemplate.delete(redisTemplate.keys("job:status:" + USER_ID + ":*"));
    }

    @Test
    @DisplayName("submit()은 worker 실행을 기다리지 않고 즉시 return한다 — 진짜 async 검증")
    void submitReturnsImmediatelyWhileWorkerRunsAsync() throws Exception {
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch workerReleased = new CountDownLatch(1);
        AtomicLong submitElapsedMs = new AtomicLong(-1);

        // analysisService.run을 1초 block하도록 stub. submit이 이를 기다리면 sync, 안 기다리면 async.
        given(analysisService.run(anyLong(), anyLong(), anyList(), anyList()))
                .willAnswer(invocation -> {
                    workerStarted.countDown();
                    workerReleased.await(5, TimeUnit.SECONDS);
                    return new GithubAnalysisService.GithubAnalysisResult(
                            1L, 1, null, "summary", java.time.Instant.now(), null
                    );
                });

        long startMs = System.currentTimeMillis();
        String jobId = orchestrator.submit(USER_ID, 1L, List.of(1L), List.of(1L));
        submitElapsedMs.set(System.currentTimeMillis() - startMs);

        // submit은 매우 빠르게 (worker가 끝나기 전에) return해야 함
        assertThat(submitElapsedMs.get())
                .as("submit should return immediately (true async); was %d ms", submitElapsedMs.get())
                .isLessThan(500L);
        assertThat(jobId).isNotBlank();

        // worker는 별도 thread에서 진행 중이어야 함
        assertThat(workerStarted.await(2, TimeUnit.SECONDS))
                .as("worker should start in background thread")
                .isTrue();

        // job 상태는 REQUESTED → RUNNING 으로 전이 가능 (worker가 첫 save를 했을 수 있음)
        Optional<JobStatusSnapshot> snapshot = jobStatusService.find(USER_ID, jobId);
        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().status())
                .isIn(JobStatus.REQUESTED, JobStatus.RUNNING);

        // worker 해제 후 SUCCEEDED 도달까지 대기
        workerReleased.countDown();
        boolean reachedTerminal = waitForTerminal(jobId, 10_000);
        assertThat(reachedTerminal).as("worker should reach terminal state").isTrue();
    }

    private boolean waitForTerminal(String jobId, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Optional<JobStatusSnapshot> snapshot = jobStatusService.find(USER_ID, jobId);
            if (snapshot.isPresent()) {
                JobStatus status = snapshot.get().status();
                if (status == JobStatus.SUCCEEDED || status == JobStatus.FAILED) {
                    return true;
                }
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        return false;
    }
}
