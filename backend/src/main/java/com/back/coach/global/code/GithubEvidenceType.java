package com.back.coach.global.code;

public enum GithubEvidenceType implements CodeEnum {
    README,
    CODE,
    CONFIG,
    REPO_METADATA,
    COMMIT;

    @Override
    public String code() {
        return name();
    }
}
