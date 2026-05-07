package com.back.coach.global.code;

public enum PatternSeverity implements CodeEnum {
    LOW,
    MEDIUM,
    HIGH;

    @Override
    public String code() {
        return name();
    }
}
