package com.back.coach.global.code;

public enum SkillSourceType implements CodeEnum {
    USER_INPUT,
    GITHUB_ESTIMATED,
    SYSTEM_DERIVED;

    @Override
    public String code() {
        return name();
    }
}
