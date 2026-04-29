package com.back.coach.global.security;

import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;

import java.util.Objects;

public final class OwnershipValidator {

    private OwnershipValidator() {
    }

    public static void requireOwned(boolean owned) {
        if (!owned) {
            throw new ServiceException(ErrorCode.FORBIDDEN);
        }
    }

    public static void requireSameUser(Long expectedUserId, Long actualUserId) {
        if (expectedUserId == null || !Objects.equals(expectedUserId, actualUserId)) {
            throw new ServiceException(ErrorCode.FORBIDDEN);
        }
    }
}
