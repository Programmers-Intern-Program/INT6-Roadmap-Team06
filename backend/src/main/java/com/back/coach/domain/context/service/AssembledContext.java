package com.back.coach.domain.context.service;

import com.back.coach.global.code.CoachTemplate;

/**
 * Context Manager가 조립한 시스템 프롬프트와 사용 메타데이터.
 *
 * @param systemPrompt LLM에 전달할 system instruction
 * @param template     사용된 템플릿 (Tier 결정의 근거)
 * @param activeSignalCount Context Manager가 조립한 activeSignals 수 (자동 승격 판단용)
 */
public record AssembledContext(
        String systemPrompt,
        CoachTemplate template,
        int activeSignalCount
) {
}
