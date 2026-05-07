package com.back.coach.global.code;

public enum PatternType implements CodeEnum {
    REPEATED_INCOMPLETE,
    CONSECUTIVE_DELAY,
    SKILL_REPEATED_FAILURE,
    GOAL_DRIFT_CANDIDATE;

    @Override
    public String code() {
        return name();
    }
}
