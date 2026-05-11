package com.back.coach.domain.job.service;

import com.back.coach.global.code.JobStatus;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Finalized (SUCCEEDED/FAILED) 잡 이력을 Redis LIST에 저장한다.
 * NBE8-10-final-Team02 FailedJobRedisStore 패턴 차용 (성공도 함께 보관하도록 확장).
 *
 * Key: job:history:{userId}
 * 캡: 최신 50건 (LPUSH + LTRIM)
 * TTL: 7일
 */
@Service
public class JobHistoryService {

    static final String KEY_PREFIX = "job:history:";
    static final Duration HISTORY_TTL = Duration.ofDays(7);
    static final long HISTORY_LIMIT = 50;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public JobHistoryService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void record(Long userId, JobHistoryEntry entry) {
        validateUserId(userId);
        if (entry == null) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "entry는 필수입니다.");
        }
        if (entry.status() != JobStatus.SUCCEEDED && entry.status() != JobStatus.FAILED) {
            throw new ServiceException(ErrorCode.INVALID_INPUT,
                    "finalized 상태(SUCCEEDED/FAILED)만 history에 저장할 수 있습니다.");
        }

        String key = key(userId);
        String payload = writeEntry(entry);
        redisTemplate.opsForList().leftPush(key, payload);
        redisTemplate.opsForList().trim(key, 0, HISTORY_LIMIT - 1);
        redisTemplate.expire(key, HISTORY_TTL);
    }

    public List<JobHistoryEntry> findRecent(Long userId, int limit) {
        validateUserId(userId);
        if (limit < 1) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "limit는 1 이상이어야 합니다.");
        }
        long upper = Math.min(limit, HISTORY_LIMIT) - 1L;
        List<String> payloads = redisTemplate.opsForList().range(key(userId), 0, upper);
        if (payloads == null || payloads.isEmpty()) {
            return List.of();
        }
        List<JobHistoryEntry> entries = new ArrayList<>(payloads.size());
        for (String payload : payloads) {
            entries.add(readEntry(payload));
        }
        return entries;
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId < 1) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "userId는 양수여야 합니다.");
        }
    }

    static String key(Long userId) {
        return KEY_PREFIX + userId;
    }

    private String writeEntry(JobHistoryEntry entry) {
        try {
            return objectMapper.writeValueAsString(entry);
        } catch (JsonProcessingException e) {
            throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "JobHistory 저장 payload 직렬화에 실패했습니다.");
        }
    }

    private JobHistoryEntry readEntry(String payload) {
        try {
            return objectMapper.readValue(payload, JobHistoryEntry.class);
        } catch (JsonProcessingException e) {
            throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "JobHistory payload를 읽을 수 없습니다.");
        }
    }

    public static JobHistoryEntry from(String jobType, JobStatusSnapshot snapshot) {
        return new JobHistoryEntry(
                snapshot.jobId(),
                jobType,
                snapshot.status(),
                snapshot.currentStep(),
                snapshot.error(),
                snapshot.resultId(),
                Instant.now()
        );
    }
}
