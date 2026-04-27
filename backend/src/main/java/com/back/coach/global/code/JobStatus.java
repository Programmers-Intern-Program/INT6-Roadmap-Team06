package com.back.coach.global.code;

public enum JobStatus implements CodeEnum {
    REQUESTED,
    RUNNING,
    SUCCEEDED,
    FAILED;

    @Override
    public String code() {
        return name();
    }
}
