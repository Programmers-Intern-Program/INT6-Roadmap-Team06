package com.back.coach.domain.job.service;

import com.back.coach.global.code.JobStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 백엔드 시작 시 zombie 잡(이전 인스턴스 종료 시 RUNNING/REQUESTED 상태로 남은 것)을 정리한다.
 *
 * 시나리오:
 *   1. 잡 처리 중 백엔드 crash/재시작
 *   2. in-memory @Async worker thread는 종료됨
 *   3. Redis 잡 상태는 RUNNING/REQUESTED 그대로 (24h TTL까지 유지)
 *   4. 사용자에게 "영원히 진행 중"으로 표시됨
 *
 * 해결: ApplicationReadyEvent 시점에 RUNNING/REQUESTED 잡을 모두 FAILED("backend restarted")로 마킹.
 * 결과는 잡 이력(JobHistoryService)에도 기록되어 사용자가 "재시작으로 실패" 메시지를 확인할 수 있다.
 *
 * 가정: 단일 백엔드 인스턴스. 멀티 인스턴스 환경에선 다른 인스턴스가 처리 중인 잡까지 FAILED로
 * 만들 위험 있음 → leader election + lease 기반으로 확장 필요 (현재 범위 밖).
 */
@Service
public class JobRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(JobRecoveryService.class);
    private static final String SCAN_PATTERN = JobStatusService.KEY_PREFIX + "*";
    private static final int SCAN_BATCH = 100;
    static final String RECOVERY_STEP = "RECOVERED_AFTER_RESTART";
    static final String RECOVERY_ERROR = "백엔드 재시작으로 작업이 중단되었습니다. 다시 시도해주세요.";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final JobStatusService jobStatusService;
    private final JobHistoryService jobHistoryService;
    private final boolean recoveryEnabled;

    public JobRecoveryService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            JobStatusService jobStatusService,
            JobHistoryService jobHistoryService,
            @Value("${coach.job.recovery.enabled:true}") boolean recoveryEnabled
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.jobStatusService = jobStatusService;
        this.jobHistoryService = jobHistoryService;
        this.recoveryEnabled = recoveryEnabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverStaleJobsOnStartup() {
        if (!recoveryEnabled) {
            log.info("Job recovery disabled by config");
            return;
        }
        log.info("Job recovery scan started (pattern={})", SCAN_PATTERN);
        int scanned = 0;
        int recovered = 0;
        List<KeyEntry> stale = collectStale();
        scanned = stale.size();
        for (KeyEntry entry : stale) {
            try {
                JobStatusSnapshot failed = jobStatusService.saveFailure(
                        entry.userId,
                        entry.snapshot.jobId(),
                        RECOVERY_STEP,
                        RECOVERY_ERROR
                );
                jobHistoryService.record(entry.userId, JobHistoryService.from("UNKNOWN", failed));
                recovered++;
            } catch (Exception e) {
                log.warn("Job recovery failed for key={} reason={}", entry.key, e.getMessage());
            }
        }
        log.info("Job recovery scan completed: scanned={}, recovered={}", scanned, recovered);
    }

    private List<KeyEntry> collectStale() {
        List<KeyEntry> result = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(SCAN_PATTERN).count(SCAN_BATCH).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                Optional<KeyEntry> parsed = parseEntry(key);
                parsed.filter(e -> isStale(e.snapshot.status())).ifPresent(result::add);
            }
        } catch (Exception e) {
            log.warn("Job recovery SCAN failed reason={}", e.getMessage());
        }
        return result;
    }

    private Optional<KeyEntry> parseEntry(String key) {
        // key shape: job:status:{userId}:{jobId}
        String suffix = key.substring(JobStatusService.KEY_PREFIX.length());
        int sep = suffix.indexOf(':');
        if (sep < 0) {
            return Optional.empty();
        }
        Long userId;
        try {
            userId = Long.parseLong(suffix.substring(0, sep));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        String payload = redisTemplate.opsForValue().get(key);
        if (payload == null) {
            return Optional.empty();
        }
        try {
            JobStatusSnapshot snapshot = objectMapper.readValue(payload, JobStatusSnapshot.class);
            return Optional.of(new KeyEntry(key, userId, snapshot));
        } catch (JsonProcessingException e) {
            log.warn("Job recovery payload deserialize failed key={} reason={}", key, e.getMessage());
            return Optional.empty();
        }
    }

    private boolean isStale(JobStatus status) {
        return status == JobStatus.REQUESTED || status == JobStatus.RUNNING;
    }

    private record KeyEntry(String key, Long userId, JobStatusSnapshot snapshot) {
    }
}
