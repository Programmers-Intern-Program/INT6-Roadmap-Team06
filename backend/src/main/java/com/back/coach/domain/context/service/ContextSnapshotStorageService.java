package com.back.coach.domain.context.service;

import com.back.coach.domain.context.entity.UserContextSnapshot;
import com.back.coach.domain.context.repository.UserContextSnapshotRepository;
import com.back.coach.global.code.ContextType;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContextSnapshotStorageService {

    private final UserContextSnapshotRepository userContextSnapshotRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserContextSnapshot createSnapshot(Long userId, ContextType contextType, String payload) {
        validateRequest(userId, contextType, payload);

        Instant now = Instant.now();
        userContextSnapshotRepository.findActiveByUserIdAndContextType(userId, contextType)
                .ifPresent(activeSnapshot -> activeSnapshot.close(now));

        int nextVersion = nextVersion(
                userContextSnapshotRepository.findMaxVersionByUserIdAndContextType(userId, contextType)
        );
        UserContextSnapshot snapshot = UserContextSnapshot.create(userId, contextType, nextVersion, payload, now);
        return userContextSnapshotRepository.save(snapshot);
    }

    public Optional<UserContextSnapshot> findActiveSnapshot(Long userId, ContextType contextType) {
        return userContextSnapshotRepository.findActiveByUserIdAndContextType(userId, contextType);
    }

    public Optional<UserContextSnapshot> findLatestSnapshot(Long userId, ContextType contextType) {
        return userContextSnapshotRepository.findLatestByUserIdAndContextType(userId, contextType);
    }

    private int nextVersion(Integer currentMaxVersion) {
        if (currentMaxVersion == null) {
            return 1;
        }
        return currentMaxVersion + 1;
    }

    private void validateRequest(Long userId, ContextType contextType, String payload) {
        if (userId == null || userId < 1) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "userId는 양수여야 합니다.");
        }
        if (contextType == null) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "contextType은 필수입니다.");
        }
        if (payload == null || payload.isBlank()) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "payload는 필수입니다.");
        }
        validatePayload(contextType, payload);
    }

    private void validatePayload(ContextType contextType, String payload) {
        JsonNode root = readPayload(payload);
        if (!root.isObject()) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "Context Snapshot payload는 JSON object여야 합니다.");
        }
        if (!contextType.code().equals(root.path("contextType").asText(null))) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "payload.contextType이 contextType과 일치해야 합니다.");
        }
        if (!root.path("sourceRefs").isObject()) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "payload.sourceRefs는 필수 object입니다.");
        }
    }

    private JsonNode readPayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "payload는 유효한 JSON이어야 합니다.");
        }
    }
}
