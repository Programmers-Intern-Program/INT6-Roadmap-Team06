package com.back.coach.global.code;

public enum ProgressStatus implements CodeEnum {
    TODO,
    IN_PROGRESS,
    DONE,
    SKIPPED;

    @Override
    public String code() {
        return name();
    }
}
