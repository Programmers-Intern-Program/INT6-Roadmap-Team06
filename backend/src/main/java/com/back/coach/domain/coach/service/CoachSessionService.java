package com.back.coach.domain.coach.service;

import com.back.coach.domain.coach.entity.ChatSession;
import com.back.coach.domain.coach.repository.ChatSessionRepository;
import com.back.coach.domain.context.entity.UserContextSnapshot;
import com.back.coach.domain.context.repository.UserContextSnapshotRepository;
import com.back.coach.domain.context.service.ContextSnapshotPublisher;
import com.back.coach.global.code.ChatSessionStatus;
import com.back.coach.global.code.ContextType;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

@Service
public class CoachSessionService {

    private static final Logger log = LoggerFactory.getLogger(CoachSessionService.class);

    // Publisher가 생성하는 진짜 PROFILE/PLAN payload는 최소 700+ bytes (profile + skills + github + diagnosis 합본).
    // SQL seed / 빈 객체 placeholder는 250–300 bytes 영역. 안전한 gap 으로 500 byte 임계값 사용.
    static final int PLACEHOLDER_PAYLOAD_BYTES_THRESHOLD = 500;

    private final ChatSessionRepository chatSessionRepository;
    private final UserContextSnapshotRepository contextSnapshotRepository;
    private final ContextSnapshotPublisher contextSnapshotPublisher;
    private final TransactionTemplate transactionTemplate;

    public CoachSessionService(
            ChatSessionRepository chatSessionRepository,
            UserContextSnapshotRepository contextSnapshotRepository,
            ContextSnapshotPublisher contextSnapshotPublisher,
            TransactionTemplate transactionTemplate
    ) {
        this.chatSessionRepository = chatSessionRepository;
        this.contextSnapshotRepository = contextSnapshotRepository;
        this.contextSnapshotPublisher = contextSnapshotPublisher;
        this.transactionTemplate = transactionTemplate;
    }

    // 트랜잭션 밖에서 self-heal publish 먼저 실행 후, 짧은 auto-tx로 세션 생성.
    // self-heal: SQL seed / migration 등으로 placeholder snapshot이 남아 있는 경우
    //            publisher가 트리거되지 않아 빈 컨텍스트로 세션이 시작되는 버그 방어.
    public ChatSession startSession(Long userId) {
        refreshSnapshotsIfPlaceholder(userId);
        return transactionTemplate.execute(status -> doStartSession(userId));
    }

    private ChatSession doStartSession(Long userId) {
        UserContextSnapshot profileSnapshot = contextSnapshotRepository
                .findActiveByUserIdAndContextType(userId, ContextType.PROFILE)
                .orElseThrow(() -> new ServiceException(
                        ErrorCode.SNAPSHOT_NOT_FOUND,
                        "PROFILE snapshot이 없습니다. 프로필을 먼저 저장해주세요."
                ));

        UserContextSnapshot planSnapshot = contextSnapshotRepository
                .findActiveByUserIdAndContextType(userId, ContextType.PLAN)
                .orElseThrow(() -> new ServiceException(
                        ErrorCode.SNAPSHOT_NOT_FOUND,
                        "PLAN snapshot이 없습니다. 로드맵을 먼저 생성해주세요."
                ));

        ChatSession session = ChatSession.start(
                userId,
                profileSnapshot.getVersion(),
                planSnapshot.getVersion()
        );

        return chatSessionRepository.save(session);
    }

    /**
     * Placeholder snapshot이 이미 존재할 때만 publisher 호출 (self-heal).
     * snapshot이 아예 없는 경우는 v1 입력 미완 = 사용자에게 SNAPSHOT_NOT_FOUND를 그대로 노출.
     * outer tx 밖에서 동기 실행되므로 새 active snapshot이 즉시 가시화된다.
     * payload가 풍부하면 skip → churn 0.
     */
    private void refreshSnapshotsIfPlaceholder(Long userId) {
        contextSnapshotRepository.findActiveByUserIdAndContextType(userId, ContextType.PROFILE)
                .filter(this::isPlaceholder)
                .ifPresent(stale -> {
                    log.info("PROFILE snapshot self-heal: publishing for userId={} (current bytes={})",
                            userId, stale.getPayload() == null ? 0 : stale.getPayload().length());
                    contextSnapshotPublisher.publishProfile(userId);
                });

        contextSnapshotRepository.findActiveByUserIdAndContextType(userId, ContextType.PLAN)
                .filter(this::isPlaceholder)
                .ifPresent(stale -> {
                    log.info("PLAN snapshot self-heal: publishing for userId={} (current bytes={})",
                            userId, stale.getPayload() == null ? 0 : stale.getPayload().length());
                    contextSnapshotPublisher.publishPlan(userId);
                });
    }

    private boolean isPlaceholder(UserContextSnapshot snapshot) {
        String payload = snapshot.getPayload();
        return payload == null || payload.length() < PLACEHOLDER_PAYLOAD_BYTES_THRESHOLD;
    }

    @Transactional(readOnly = true)
    public ChatSession getActiveSession(Long userId) {
        return chatSessionRepository
                .findFirstByUserIdAndStatusOrderByStartedAtDesc(userId, ChatSessionStatus.ACTIVE)
                .orElseThrow(() -> new ServiceException(ErrorCode.SESSION_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<ChatSession> getSessions(Long userId) {
        return chatSessionRepository.findByUserIdOrderByStartedAtDesc(userId);
    }

    @Transactional
    public void closeSession(Long userId, Long sessionId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ServiceException(ErrorCode.SESSION_NOT_FOUND));

        if (!session.isOwnedBy(userId)) {
            throw new ServiceException(ErrorCode.FORBIDDEN);
        }

        if (session.isClosed()) {
            return;
        }

        session.close(Instant.now());
    }
}
