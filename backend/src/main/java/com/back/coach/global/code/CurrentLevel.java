package com.back.coach.global.code;

public enum CurrentLevel implements CodeEnum {
    BEGINNER,
    BASIC,
    JUNIOR,
    INTERMEDIATE,
    ADVANCED;

    @Override
    public String code() {
        return name();
    }
}
