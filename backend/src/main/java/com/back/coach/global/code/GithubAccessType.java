package com.back.coach.global.code;

public enum GithubAccessType implements CodeEnum {
    OAUTH;

    @Override
    public String code() {
        return name();
    }
}
