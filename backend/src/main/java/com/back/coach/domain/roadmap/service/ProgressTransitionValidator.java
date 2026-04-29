package com.back.coach.domain.roadmap.service;

import com.back.coach.global.code.ProgressStatus;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;

import java.util.Map;
import java.util.Set;

public final class ProgressTransitionValidator {

    private static final Map<ProgressStatus, Set<ProgressStatus>> ALLOWED_TRANSITIONS = Map.of(
            ProgressStatus.TODO, Set.of(ProgressStatus.IN_PROGRESS, ProgressStatus.DONE, ProgressStatus.SKIPPED),
            ProgressStatus.IN_PROGRESS, Set.of(ProgressStatus.DONE, ProgressStatus.SKIPPED),
            ProgressStatus.DONE, Set.of(ProgressStatus.IN_PROGRESS),
            ProgressStatus.SKIPPED, Set.of(ProgressStatus.IN_PROGRESS)
    );

    private ProgressTransitionValidator() {
    }

    public static void validate(ProgressStatus currentStatus, ProgressStatus nextStatus) {
        if (nextStatus == null) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "진도 상태는 필수입니다.");
        }

        ProgressStatus normalizedCurrentStatus = currentStatus == null ? ProgressStatus.TODO : currentStatus;
        if (!ALLOWED_TRANSITIONS.getOrDefault(normalizedCurrentStatus, Set.of()).contains(nextStatus)) {
            throw new ServiceException(ErrorCode.CONFLICT, "허용되지 않는 진도 상태 전이입니다.");
        }
    }
}
