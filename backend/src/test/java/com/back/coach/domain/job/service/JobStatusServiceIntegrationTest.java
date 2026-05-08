package com.back.coach.domain.job.service;

import com.back.coach.global.code.JobStatus;
import com.back.coach.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class JobStatusServiceIntegrationTest {

    private static final Long USER_ID = 10L;

    @Autowired
    private JobStatusService jobStatusService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final List<String> keysToDelete = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        if (!keysToDelete.isEmpty()) {
            redisTemplate.delete(keysToDelete);
        }
    }

    @Test
    @DisplayName("JobStatus 저장 후 Redis에서 조회할 수 있다")
    void saveAndFindJobStatus() {
        String jobId = nextJobId();

        JobStatusSnapshot saved = jobStatusService.save(
                USER_ID, jobId, JobStatus.REQUESTED, "REQUEST_ACCEPTED"
        );

        assertThat(saved).isEqualTo(new JobStatusSnapshot(
                jobId, JobStatus.REQUESTED, "REQUEST_ACCEPTED", null
        ));
        assertThat(jobStatusService.find(USER_ID, jobId))
                .hasValue(new JobStatusSnapshot(jobId, JobStatus.REQUESTED, "REQUEST_ACCEPTED", null));
    }

    @Test
    @DisplayName("같은 jobId 저장은 상태와 단계를 최신 값으로 갱신한다")
    void saveUpdatesExistingJobStatus() {
        String jobId = nextJobId();
        jobStatusService.save(USER_ID, jobId, JobStatus.REQUESTED, "REQUEST_ACCEPTED");

        jobStatusService.save(USER_ID, jobId, JobStatus.RUNNING, "FETCH_REPOSITORIES");

        assertThat(jobStatusService.find(USER_ID, jobId))
                .hasValue(new JobStatusSnapshot(jobId, JobStatus.RUNNING, "FETCH_REPOSITORIES", null));
    }

    @Test
    @DisplayName("실패 상태와 error를 함께 저장한다")
    void saveFailureStoresFailedStatusAndError() {
        String jobId = nextJobId();

        jobStatusService.saveFailure(USER_ID, jobId, "LLM_SUMMARY", "LLM timeout");

        assertThat(jobStatusService.find(USER_ID, jobId))
                .hasValue(new JobStatusSnapshot(jobId, JobStatus.FAILED, "LLM_SUMMARY", "LLM timeout"));
    }

    @Test
    @DisplayName("저장된 JobStatus key에는 TTL이 적용된다")
    void saveAppliesTtl() {
        String jobId = nextJobId();

        jobStatusService.save(USER_ID, jobId, JobStatus.RUNNING, "SYNTHESIZE_RESULT");

        Long ttlSeconds = redisTemplate.getExpire(JobStatusService.key(USER_ID, jobId), TimeUnit.SECONDS);
        assertThat(ttlSeconds)
                .isNotNull()
                .isGreaterThan(0L)
                .isLessThanOrEqualTo(JobStatusService.JOB_STATUS_TTL.toSeconds());
    }

    @Test
    @DisplayName("존재하지 않는 jobId 조회는 Optional.empty를 반환한다")
    void findWhenJobStatusDoesNotExistReturnsEmpty() {
        String jobId = nextJobId();

        assertThat(jobStatusService.find(USER_ID, jobId)).isEmpty();
    }

    private String nextJobId() {
        String jobId = "job-" + UUID.randomUUID();
        keysToDelete.add(JobStatusService.key(USER_ID, jobId));
        return jobId;
    }
}
