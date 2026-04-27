package com.back.coach.global.code;

public enum GithubDepthLevel implements CodeEnum {
    INTRO,
    APPLIED,
    PRACTICAL,
    DEEP;

    @Override
    public String code() {
        return name();
    }
}
