package com.back.coach.global.code;

public enum CoachMessageRole implements CodeEnum {
    USER,
    COACH,
    SUMMARY;

    @Override
    public String code() {
        return name();
    }
}
