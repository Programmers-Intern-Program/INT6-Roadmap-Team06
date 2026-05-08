package com.back.coach.global.code;

public enum ChatSessionStatus implements CodeEnum {
    ACTIVE,
    CLOSED;

    @Override
    public String code() {
        return name();
    }
}
