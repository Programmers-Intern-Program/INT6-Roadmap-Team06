package com.back.coach.global.code;

public enum RoadmapTaskType implements CodeEnum {
    READ_DOCS,
    BUILD_EXAMPLE,
    WRITE_NOTE,
    APPLY_PROJECT,
    REVIEW;

    @Override
    public String code() {
        return name();
    }
}
