package com.back.coach.global.code;

public enum MaterialType implements CodeEnum {
    DOCS,
    ARTICLE,
    REPOSITORY,
    VIDEO,
    TEMPLATE;

    @Override
    public String code() {
        return name();
    }
}
