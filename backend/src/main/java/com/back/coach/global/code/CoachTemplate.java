package com.back.coach.global.code;

/**
 * Coach 처리 상황별 use-case 템플릿.
 *
 * <p>3-Tier(읽는 정보량)와 별도. docs/17_v2_context_tier_assembly.md 참조.
 */
public enum CoachTemplate implements CodeEnum {

    /** Tier 1 — 가벼운 기본 상태. 오늘 할 일, 현재 주차 등. */
    COACH_LIGHTWEIGHT,

    /** Tier 3 — 판단 맥락까지. 재계획/재분석 판단 시. */
    COACH_FULL_CONTEXT;

    @Override
    public String code() {
        return name();
    }
}
