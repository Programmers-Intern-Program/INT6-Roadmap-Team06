package com.back.coach.global.code;

public enum ReplanProposalStatus implements CodeEnum {
    PENDING, CONFIRMED, DISMISSED, EXPIRED;

    @Override
    public String code() {
        return name();
    }
}
