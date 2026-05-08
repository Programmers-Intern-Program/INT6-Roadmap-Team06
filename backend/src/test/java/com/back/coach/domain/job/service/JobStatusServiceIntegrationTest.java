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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    @DisplayName("여러 jobId 상태를 한 번에 조회한다")
    void findAllReturnsJobStatusesInRequestedOrder() {
        String firstJobId = nextJobId();
        String secondJobId = nextJobId();
        jobStatusService.save(USER_ID, firstJobId, JobStatus.REQUESTED, "REQUEST_ACCEPTED");
        jobStatusService.save(USER_ID, secondJobId, JobStatus.RUNNING, "FETCH_REPOSITORIES");

        Map<String, JobStatusSnapshot> result = jobStatusService.findAll(
                USER_ID,
                List.of(firstJobId, secondJobId)
        );

        assertThat(result)
                .containsExactly(
                        Map.entry(firstJobId, new JobStatusSnapshot(firstJobId, JobStatus.REQUESTED, "REQUEST_ACCEPTED", null)),
                        Map.entry(secondJobId, new JobStatusSnapshot(secondJobId, JobStatus.RUNNING, "FETCH_REPOSITORIES", null))
                );
    }

    @Test
    @DisplayName("없는 jobId가 섞이면 존재하는 JobStatus만 반환한다")
    void findAllExcludesMissingJobStatus() {
        String existingJobId = nextJobId();
        String missingJobId = nextJobId();
        jobStatusService.saveFailure(USER_ID, existingJobId, "LLM_SUMMARY", "LLM timeout");

        Map<String, JobStatusSnapshot> result = jobStatusService.findAll(
                USER_ID,
                List.of(existingJobId, missingJobId)
        );

        assertThat(result)
                .containsExactly(Map.entry(
                        existingJobId,
                        new JobStatusSnapshot(existingJobId, JobStatus.FAILED, "LLM_SUMMARY", "LLM timeout")
                ));
    }

    @Test
    @DisplayName("중복 jobId가 있어도 한 번만 반환한다")
    void findAllDeduplicatesJobIds() {
        String jobId = nextJobId();
        jobStatusService.save(USER_ID, jobId, JobStatus.SUCCEEDED, "DONE");

        Map<String, JobStatusSnapshot> result = jobStatusService.findAll(
                USER_ID,
                List.of(jobId, jobId)
        );

        assertThat(result)
                .containsExactly(Map.entry(
                        jobId,
                        new JobStatusSnapshot(jobId, JobStatus.SUCCEEDED, "DONE", null)
                ));
    }

    @Test
    @DisplayName("빈 jobId 목록 조회는 빈 Map을 반환한다")
    void findAllWhenJobIdsAreEmptyReturnsEmptyMap() {
        assertThat(jobStatusService.findAll(USER_ID, List.of())).isEmpty();
    }

    @Test
    @DisplayName("blank jobId가 섞이면 INVALID_INPUT 예외를 던진다")
    void findAllWhenJobIdsContainBlankThrowsInvalidInput() {
        assertThatThrownBy(() -> jobStatusService.findAll(USER_ID, List.of("job-1", " ")))
                .isInstanceOf(com.back.coach.global.exception.ServiceException.class)
                .hasMessage("jobId는 필수입니다.");
    }

    private String nextJobId() {
        String jobId = "job-" + UUID.randomUUID();
        keysToDelete.add(JobStatusService.key(USER_ID, jobId));
        return jobId;
    }
}
