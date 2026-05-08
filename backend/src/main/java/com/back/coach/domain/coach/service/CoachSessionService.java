package com.back.coach.domain.coach.service;

import com.back.coach.domain.coach.entity.ChatSession;
import com.back.coach.domain.coach.repository.ChatSessionRepository;
import com.back.coach.domain.context.entity.UserContextSnapshot;
import com.back.coach.domain.context.repository.UserContextSnapshotRepository;
import com.back.coach.global.code.ContextType;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class CoachSessionService {

    private final ChatSessionRepository chatSessionRepository;
    private final UserContextSnapshotRepository contextSnapshotRepository;

    public CoachSessionService(
            ChatSessionRepository chatSessionRepository,
            UserContextSnapshotRepository contextSnapshotRepository
    ) {
        this.chatSessionRepository = chatSessionRepository;
        this.contextSnapshotRepository = contextSnapshotRepository;
    }

    @Transactional
    public ChatSession startSession(Long userId) {
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
