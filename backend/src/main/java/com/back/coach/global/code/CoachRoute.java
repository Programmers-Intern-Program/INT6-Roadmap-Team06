package com.back.coach.global.code;

public enum CoachRoute implements CodeEnum {
    SIMPLE_GUIDE,
    REPLAN_SUGGEST,
    REPLAN_EXECUTE,
    DISMISS;

    @Override
    public String code() {
        return name();
    }
}
