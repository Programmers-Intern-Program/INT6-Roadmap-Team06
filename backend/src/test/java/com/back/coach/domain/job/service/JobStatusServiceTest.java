package com.back.coach.domain.job.service;

import com.back.coach.global.code.JobStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JobStatusServiceTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private JobStatusService jobStatusService;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        jobStatusService = new JobStatusService(redisTemplate, objectMapper);
    }

    @Test
    void findAll_usesSingleMultiGetWithOrderedKeys() throws Exception {
        JobStatusSnapshot first = new JobStatusSnapshot("job-1", JobStatus.REQUESTED, "REQUEST_ACCEPTED", null, null);
        JobStatusSnapshot second = new JobStatusSnapshot("job-2", JobStatus.RUNNING, "FETCH_REPOSITORIES", null, null);
        given(valueOperations.multiGet(List.of(
                "job:status:10:job-1",
                "job:status:10:job-2"
        ))).willReturn(List.of(
                objectMapper.writeValueAsString(first),
                objectMapper.writeValueAsString(second)
        ));

        Map<String, JobStatusSnapshot> result = jobStatusService.findAll(10L, List.of("job-1", "job-2"));

        assertThat(result)
                .containsExactly(
                        Map.entry("job-1", first),
                        Map.entry("job-2", second)
                );
        verify(valueOperations).multiGet(List.of(
                "job:status:10:job-1",
                "job:status:10:job-2"
        ));
        verify(valueOperations, never()).get(anyString());
    }
}
