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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 * 활성 신호는 detected_patterns 미처리 row에서 직접 추출한다 (CONVERSATION snapshot
 * 조립 파이프라인이 완성되기 전 임시 폴백).
 */
@Service
@Transactional(readOnly = true)
public class ContextManagerService {

    private final UserContextSnapshotRepository contextSnapshotRepository;
    private final DetectedPatternRepository detectedPatternRepository;

    public ContextManagerService(
            UserContextSnapshotRepository contextSnapshotRepository,
            DetectedPatternRepository detectedPatternRepository
    ) {
        this.contextSnapshotRepository = contextSnapshotRepository;
        this.detectedPatternRepository = detectedPatternRepository;
    }

    /**
     * 자동 템플릿 선택: 활성 신호가 있으면 Tier 3, 없으면 Tier 1.
     */
    public AssembledContext assembleAuto(ChatSession session, String userMessage) {
        List<DetectedPattern> activeSignals = detectedPatternRepository
                .findByUserIdAndProcessedAtIsNullOrderByCreatedAtDesc(session.getUserId());
        CoachTemplate template = activeSignals.isEmpty()
                ? CoachTemplate.COACH_LIGHTWEIGHT
                : CoachTemplate.COACH_FULL_CONTEXT;
        return assemble(session, template, userMessage, activeSignals);
    }

    /**
     * 명시 템플릿으로 조립.
     */
    public AssembledContext assemble(ChatSession session, CoachTemplate template, String userMessage) {
        List<DetectedPattern> activeSignals = template == CoachTemplate.COACH_FULL_CONTEXT
                ? detectedPatternRepository.findByUserIdAndProcessedAtIsNullOrderByCreatedAtDesc(session.getUserId())
                : List.of();
        return assemble(session, template, userMessage, activeSignals);
    }

    private AssembledContext assemble(
            ChatSession session,
            CoachTemplate template,
            String userMessage,
            List<DetectedPattern> activeSignals
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
            sb.append("# 활성 신호 (Pattern Detector 미처리)\n");
            if (activeSignals.isEmpty()) {
                sb.append("없음\n\n");
            } else {
                for (DetectedPattern signal : activeSignals) {
                    sb.append("- type=").append(signal.getPatternType())
                            .append(", severity=").append(signal.getSeverity())
                            .append(", metadata=").append(signal.getMetadata())
                            .append("\n");
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

    private UserContextSnapshot loadPinned(Long userId, ContextType type, Integer version) {
        return contextSnapshotRepository.findByUserIdAndContextTypeAndVersion(userId, type, version)
                .orElseThrow(() -> new ServiceException(
                        ErrorCode.SNAPSHOT_NOT_FOUND,
                        "세션에 고정된 " + type + " snapshot version=" + version + " 을(를) 찾을 수 없습니다."
                ));
    }
}
