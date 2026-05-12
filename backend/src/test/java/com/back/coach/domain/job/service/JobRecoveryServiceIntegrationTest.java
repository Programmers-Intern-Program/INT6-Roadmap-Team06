package com.back.coach.domain.job.service;

import com.back.coach.global.code.JobStatus;
import com.back.coach.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class JobRecoveryServiceIntegrationTest {

    private static final Long USER_ID = 9_999_001L;

    @Autowired
    private JobRecoveryService jobRecoveryService;

    @Autowired
    private JobStatusService jobStatusService;

    @Autowired
    private JobHistoryService jobHistoryService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private long jobCounter;

    @BeforeEach
    void resetCounter() {
        jobCounter = System.nanoTime();
    }

    @AfterEach
    void cleanUp() {
        redisTemplate.delete(redisTemplate.keys(JobStatusService.KEY_PREFIX + "*"));
        redisTemplate.delete(JobHistoryService.key(USER_ID));
    }

    @Test
    @DisplayName("RUNNING 잡은 startup recovery에서 FAILED로 마킹된다")
    void runningJobRecoveredToFailed() {
        String jobId = nextJobId();
        jobStatusService.save(USER_ID, jobId, JobStatus.RUNNING, "RUN_ANALYSIS");

        jobRecoveryService.recoverStaleJobsOnStartup();

        Optional<JobStatusSnapshot> after = jobStatusService.find(USER_ID, jobId);
        assertThat(after).isPresent();
        assertThat(after.get().status()).isEqualTo(JobStatus.FAILED);
        assertThat(after.get().currentStep()).isEqualTo(JobRecoveryService.RECOVERY_STEP);
        assertThat(after.get().error()).isEqualTo(JobRecoveryService.RECOVERY_ERROR);
    }

    @Test
    @DisplayName("REQUESTED 잡도 FAILED로 마킹된다")
    void requestedJobRecoveredToFailed() {
        String jobId = nextJobId();
        jobStatusService.save(USER_ID, jobId, JobStatus.REQUESTED, "REQUEST_ACCEPTED");

        jobRecoveryService.recoverStaleJobsOnStartup();

        Optional<JobStatusSnapshot> after = jobStatusService.find(USER_ID, jobId);
        assertThat(after).isPresent();
        assertThat(after.get().status()).isEqualTo(JobStatus.FAILED);
    }

    @Test
    @DisplayName("이미 SUCCEEDED/FAILED 인 잡은 건드리지 않는다")
    void terminalJobUntouched() {
        String successJobId = nextJobId();
        String failedJobId = nextJobId();
        jobStatusService.saveSuccess(USER_ID, successJobId, "ANALYSIS_DONE", "42");
        jobStatusService.saveFailure(USER_ID, failedJobId, "RUN_ANALYSIS", "기존 실패");

        jobRecoveryService.recoverStaleJobsOnStartup();

        Optional<JobStatusSnapshot> success = jobStatusService.find(USER_ID, successJobId);
        Optional<JobStatusSnapshot> failed = jobStatusService.find(USER_ID, failedJobId);
        assertThat(success).hasValueSatisfying(s -> {
            assertThat(s.status()).isEqualTo(JobStatus.SUCCEEDED);
            assertThat(s.resultId()).isEqualTo("42");
        });
        assertThat(failed).hasValueSatisfying(s -> {
            assertThat(s.status()).isEqualTo(JobStatus.FAILED);
            assertThat(s.error()).isEqualTo("기존 실패");
        });
    }

    @Test
    @DisplayName("recovery된 잡은 history에도 기록된다")
    void recoveredJobAppendedToHistory() {
        String jobId = nextJobId();
        jobStatusService.save(USER_ID, jobId, JobStatus.RUNNING, "RUN_ANALYSIS");

        jobRecoveryService.recoverStaleJobsOnStartup();

        var history = jobHistoryService.findRecent(USER_ID, 10);
        assertThat(history).extracting(JobHistoryEntry::jobId).contains(jobId);
        JobHistoryEntry recovered = history.stream()
                .filter(e -> e.jobId().equals(jobId))
                .findFirst().orElseThrow();
        assertThat(recovered.status()).isEqualTo(JobStatus.FAILED);
        assertThat(recovered.error()).isEqualTo(JobRecoveryService.RECOVERY_ERROR);
    }

    private String nextJobId() {
        return "recovery-test-" + (++jobCounter);
    }
}
