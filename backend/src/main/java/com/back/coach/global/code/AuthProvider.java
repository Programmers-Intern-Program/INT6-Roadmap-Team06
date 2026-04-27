package com.back.coach.global.code;

public enum AuthProvider implements CodeEnum {
    LOCAL,
    GOOGLE,
    GITHUB;

    @Override
    public String code() {
        return name();
    }
}
