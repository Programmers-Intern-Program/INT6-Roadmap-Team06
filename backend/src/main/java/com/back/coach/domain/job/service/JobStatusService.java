package com.back.coach.domain.job.service;

import com.back.coach.global.code.JobStatus;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedSet;

@Service
public class JobStatusService {

    static final Duration JOB_STATUS_TTL = Duration.ofHours(24);
    static final String KEY_PREFIX = "job:status:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public JobStatusService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public JobStatusSnapshot save(Long userId, String jobId, JobStatus status, String currentStep) {
        validateRequired(userId, jobId, status);
        return saveSnapshot(userId, new JobStatusSnapshot(jobId, status, currentStep, null, null));
    }

    public JobStatusSnapshot saveFailure(Long userId, String jobId, String currentStep, String error) {
        validateRequired(userId, jobId, JobStatus.FAILED);
        return saveSnapshot(userId, new JobStatusSnapshot(jobId, JobStatus.FAILED, currentStep, error, null));
    }

    public JobStatusSnapshot saveSuccess(Long userId, String jobId, String currentStep, String resultId) {
        validateRequired(userId, jobId, JobStatus.SUCCEEDED);
        return saveSnapshot(userId, new JobStatusSnapshot(jobId, JobStatus.SUCCEEDED, currentStep, null, resultId));
    }

    public Optional<JobStatusSnapshot> find(Long userId, String jobId) {
        validateUserId(userId);
        validateJobId(jobId);

        String payload = redisTemplate.opsForValue().get(key(userId, jobId));
        if (payload == null) {
            return Optional.empty();
        }
        return Optional.of(readSnapshot(payload));
    }

    public Map<String, JobStatusSnapshot> findAll(Long userId, List<String> jobIds) {
        validateUserId(userId);
        SequencedSet<String> uniqueJobIds = normalizeJobIds(jobIds);
        if (uniqueJobIds.isEmpty()) {
            return Map.of();
        }

        List<String> keys = uniqueJobIds.stream()
                .map(jobId -> key(userId, jobId))
                .toList();
        List<String> payloads = redisTemplate.opsForValue().multiGet(keys);

        Map<String, JobStatusSnapshot> result = new LinkedHashMap<>();
        int index = 0;
        for (String jobId : uniqueJobIds) {
            String payload = (payloads == null || index >= payloads.size()) ? null : payloads.get(index);
            if (payload != null) {
                result.put(jobId, readSnapshot(payload));
            }
            index++;
        }
        return result;
    }

    private JobStatusSnapshot saveSnapshot(Long userId, JobStatusSnapshot snapshot) {
        redisTemplate.opsForValue().set(
                key(userId, snapshot.jobId()),
                writeSnapshot(snapshot),
                JOB_STATUS_TTL
        );
        return snapshot;
    }

    private void validateRequired(Long userId, String jobId, JobStatus status) {
        validateUserId(userId);
        validateJobId(jobId);
        if (status == null) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "status는 필수입니다.");
        }
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId < 1) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "userId는 양수여야 합니다.");
        }
    }

    private void validateJobId(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "jobId는 필수입니다.");
        }
    }

    private SequencedSet<String> normalizeJobIds(List<String> jobIds) {
        if (jobIds == null) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "jobIds는 필수입니다.");
        }
        SequencedSet<String> uniqueJobIds = new LinkedHashSet<>();
        for (String jobId : jobIds) {
            validateJobId(jobId);
            uniqueJobIds.add(jobId);
        }
        return uniqueJobIds;
    }

    private String writeSnapshot(JobStatusSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR, "JobStatus 저장 payload 직렬화에 실패했습니다.");
        }
    }

    private JobStatusSnapshot readSnapshot(String payload) {
        try {
            return objectMapper.readValue(payload, JobStatusSnapshot.class);
        } catch (JsonProcessingException e) {
            throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR, "JobStatus 저장 payload를 읽을 수 없습니다.");
        }
    }

    static String key(Long userId, String jobId) {
        return KEY_PREFIX + userId + ":" + jobId;
    }
}
