package com.back.coach.global.security;

import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OwnershipValidatorTest {

    @Test
    void requireOwned_whenOwned_doesNotThrow() {
        assertThatCode(() -> OwnershipValidator.requireOwned(true))
                .doesNotThrowAnyException();
    }

    @Test
    void requireOwned_whenNotOwned_throwsForbidden() {
        assertThatThrownBy(() -> OwnershipValidator.requireOwned(false))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void requireSameUser_whenDifferentUser_throwsForbidden() {
        assertThatThrownBy(() -> OwnershipValidator.requireSameUser(1L, 2L))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }
}
