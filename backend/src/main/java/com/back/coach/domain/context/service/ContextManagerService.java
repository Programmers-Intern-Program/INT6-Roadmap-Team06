package com.back.coach.domain.context.service;

import com.back.coach.domain.coach.entity.ChatSession;
import com.back.coach.domain.context.entity.UserContextSnapshot;
import com.back.coach.domain.context.repository.UserContextSnapshotRepository;
import com.back.coach.domain.pattern.entity.DetectedPattern;
import com.back.coach.domain.pattern.repository.DetectedPatternRepository;
import com.back.coach.global.code.CoachTemplate;
import com.back.coach.global.code.ContextType;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Coach 호출 시 사용자 상태를 시스템 프롬프트로 조립한다.
 *
 * <p>3-Tier 정책 (docs/17_v2_context_tier_assembly.md):
 * <ul>
 *   <li>Tier 1 — PROFILE + PLAN snapshot (오늘 할 일, 현재 주차)</li>
 *   <li>Tier 3 — Tier 1 + 활성 신호 + 최근 대화 맥락 (재계획/재분석 판단)</li>
 * </ul>
 *
 * <p>세션의 고정 version 기준으로 PROFILE/PLAN snapshot을 로드한다.
 * 활성 신호는 CONVERSATION snapshot의 activeSignals를 우선 사용하고,
 * snapshot이 비어 있는 전환기에는 미처리 detected_patterns를 fallback으로 요약한다.
 */
@Service
@Transactional(readOnly = true)
public class ContextManagerService {

    private final UserContextSnapshotRepository contextSnapshotRepository;
    private final DetectedPatternRepository detectedPatternRepository;
    private final ObjectMapper objectMapper;

    public ContextManagerService(
            UserContextSnapshotRepository contextSnapshotRepository,
            DetectedPatternRepository detectedPatternRepository,
            ObjectMapper objectMapper
    ) {
        this.contextSnapshotRepository = contextSnapshotRepository;
        this.detectedPatternRepository = detectedPatternRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 자동 템플릿 선택: 활성 신호가 있으면 Tier 3, 없으면 Tier 1.
     */
    public AssembledContext assembleAuto(ChatSession session, String userMessage) {
        List<ActiveSignal> activeSignals = loadActiveSignals(session.getUserId());
        CoachTemplate template = activeSignals.isEmpty()
                ? CoachTemplate.COACH_LIGHTWEIGHT
                : CoachTemplate.COACH_FULL_CONTEXT;
        return assemble(session, template, userMessage, activeSignals);
    }

    /**
     * 명시 템플릿으로 조립.
     */
    public AssembledContext assemble(ChatSession session, CoachTemplate template, String userMessage) {
        List<ActiveSignal> activeSignals = template == CoachTemplate.COACH_FULL_CONTEXT
                ? loadActiveSignals(session.getUserId())
                : List.of();
        return assemble(session, template, userMessage, activeSignals);
    }

    private AssembledContext assemble(
            ChatSession session,
            CoachTemplate template,
            String userMessage,
            List<ActiveSignal> activeSignals
    ) {
        UserContextSnapshot profile = loadPinned(session.getUserId(), ContextType.PROFILE, session.getProfileVersion());
        UserContextSnapshot plan = loadPinned(session.getUserId(), ContextType.PLAN, session.getRoadmapVersion());

        StringBuilder sb = new StringBuilder();
        sb.append("당신은 학습 코치입니다. 사용자의 질문에 짧고 실용적인 답변을 한국어로 제공합니다.\n\n");

        sb.append("# 사용자 프로필 (version ").append(profile.getVersion()).append(")\n");
        sb.append(profile.getPayload()).append("\n\n");

        sb.append("# 학습 로드맵 (version ").append(plan.getVersion()).append(")\n");
        sb.append(plan.getPayload()).append("\n\n");

        if (template == CoachTemplate.COACH_FULL_CONTEXT) {
            sb.append("# 활성 신호 (activeSignals)\n");
            if (activeSignals.isEmpty()) {
                sb.append("없음\n\n");
            } else {
                for (ActiveSignal signal : activeSignals) {
                    sb.append("- sourcePatternId=").append(signal.sourcePatternId())
                            .append(", patternType=").append(signal.patternType())
                            .append(", severity=").append(signal.severity())
                            .append(", summary=").append(signal.summary());
                    if (signal.detectedAt() != null && !signal.detectedAt().isBlank()) {
                        sb.append(", detectedAt=").append(signal.detectedAt());
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }

            // 최근 대화 요약(CONVERSATION snapshot)은 있으면 포함, 없으면 생략
            Optional<UserContextSnapshot> conversation = contextSnapshotRepository
                    .findActiveByUserIdAndContextType(session.getUserId(), ContextType.CONVERSATION);
            conversation.ifPresent(snap -> {
                sb.append("# 최근 대화 요약 (version ").append(snap.getVersion()).append(")\n");
                sb.append(snap.getPayload()).append("\n\n");
            });
        }

        sb.append("# 사용자 메시지\n").append(userMessage).append("\n");

        return new AssembledContext(sb.toString(), template, activeSignals.size());
    }

    private List<ActiveSignal> loadActiveSignals(Long userId) {
        List<ActiveSignal> snapshotSignals = loadConversationActiveSignals(userId);
        if (!snapshotSignals.isEmpty()) {
            return snapshotSignals;
        }
        return loadFallbackDetectedPatterns(userId);
    }

    private List<ActiveSignal> loadConversationActiveSignals(Long userId) {
        return contextSnapshotRepository.findActiveByUserIdAndContextType(userId, ContextType.CONVERSATION)
                .map(UserContextSnapshot::getPayload)
                .map(this::parseActiveSignals)
                .orElse(List.of());
    }

    private List<ActiveSignal> parseActiveSignals(String payload) {
        try {
            JsonNode activeSignalsNode = objectMapper.readTree(payload).path("activeSignals");
            if (!activeSignalsNode.isArray() || activeSignalsNode.isEmpty()) {
                return List.of();
            }

            List<ActiveSignal> activeSignals = new ArrayList<>();
            for (JsonNode node : activeSignalsNode) {
                activeSignals.add(new ActiveSignal(
                        text(node, "sourcePatternId"),
                        text(node, "patternType"),
                        text(node, "severity"),
                        text(node, "summary"),
                        text(node, "detectedAt")
                ));
            }
            return activeSignals;
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private List<ActiveSignal> loadFallbackDetectedPatterns(Long userId) {
        return detectedPatternRepository.findByUserIdAndProcessedAtIsNullOrderByCreatedAtDesc(userId)
                .stream()
                .map(pattern -> new ActiveSignal(
                        String.valueOf(pattern.getId()),
                        pattern.getPatternType().name(),
                        pattern.getSeverity().name(),
                        "metadata=" + pattern.getMetadata(),
                        pattern.getCreatedAt() == null ? null : pattern.getCreatedAt().toString()
                ))
                .toList();
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return "";
        }
        return value.asText();
    }

    private UserContextSnapshot loadPinned(Long userId, ContextType type, Integer version) {
        return contextSnapshotRepository.findByUserIdAndContextTypeAndVersion(userId, type, version)
                .orElseThrow(() -> new ServiceException(
                        ErrorCode.SNAPSHOT_NOT_FOUND,
                        "세션에 고정된 " + type + " snapshot version=" + version + " 을(를) 찾을 수 없습니다."
                ));
    }

    private record ActiveSignal(
            String sourcePatternId,
            String patternType,
            String severity,
            String summary,
            String detectedAt
    ) {
    }
}
