package com.back.coach.global.code;

public enum ProficiencyLevel implements CodeEnum {
    NONE,
    BASIC,
    WORKING,
    STRONG;

    @Override
    public String code() {
        return name();
    }
}
