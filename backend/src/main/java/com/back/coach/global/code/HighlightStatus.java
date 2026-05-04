package com.back.coach.global.code;

public enum HighlightStatus implements CodeEnum {
    ADOPTED,
    EVOLVED,
    REVERSED;

    @Override
    public String code() {
        return name();
    }
}
