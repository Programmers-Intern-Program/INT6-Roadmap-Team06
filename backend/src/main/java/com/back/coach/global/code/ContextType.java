package com.back.coach.global.code;

public enum ContextType implements CodeEnum {
    PROFILE,
    PLAN,
    CONVERSATION;

    @Override
    public String code() {
        return name();
    }
}
