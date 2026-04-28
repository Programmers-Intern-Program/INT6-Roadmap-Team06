package com.back.coach.domain.roadmap;

import com.back.coach.global.code.ProgressStatus;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProgressTransitionValidatorTest {

    @Test
    void validate_whenAllowedTransitions_doesNotThrow() {
        assertThatCode(() -> ProgressTransitionValidator.validate(ProgressStatus.TODO, ProgressStatus.IN_PROGRESS))
                .doesNotThrowAnyException();
        assertThatCode(() -> ProgressTransitionValidator.validate(ProgressStatus.TODO, ProgressStatus.DONE))
                .doesNotThrowAnyException();
        assertThatCode(() -> ProgressTransitionValidator.validate(ProgressStatus.TODO, ProgressStatus.SKIPPED))
                .doesNotThrowAnyException();
        assertThatCode(() -> ProgressTransitionValidator.validate(ProgressStatus.IN_PROGRESS, ProgressStatus.DONE))
                .doesNotThrowAnyException();
        assertThatCode(() -> ProgressTransitionValidator.validate(ProgressStatus.IN_PROGRESS, ProgressStatus.SKIPPED))
                .doesNotThrowAnyException();
        assertThatCode(() -> ProgressTransitionValidator.validate(ProgressStatus.DONE, ProgressStatus.IN_PROGRESS))
                .doesNotThrowAnyException();
        assertThatCode(() -> ProgressTransitionValidator.validate(ProgressStatus.SKIPPED, ProgressStatus.IN_PROGRESS))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_whenInitialStatus_allowsProgressStatusesExceptTodo() {
        assertThatCode(() -> ProgressTransitionValidator.validate(null, ProgressStatus.IN_PROGRESS))
                .doesNotThrowAnyException();
        assertThatCode(() -> ProgressTransitionValidator.validate(null, ProgressStatus.DONE))
                .doesNotThrowAnyException();
        assertThatCode(() -> ProgressTransitionValidator.validate(null, ProgressStatus.SKIPPED))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_whenInitialStatusToTodo_throwsConflict() {
        assertThatThrownBy(() -> ProgressTransitionValidator.validate(null, ProgressStatus.TODO))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void validate_whenInvalidTransitions_throwsConflict() {
        assertConflict(ProgressStatus.DONE, ProgressStatus.TODO);
        assertConflict(ProgressStatus.SKIPPED, ProgressStatus.TODO);
        assertConflict(ProgressStatus.IN_PROGRESS, ProgressStatus.TODO);
    }

    @Test
    void validate_whenSameStatus_throwsConflict() {
        assertConflict(ProgressStatus.TODO, ProgressStatus.TODO);
        assertConflict(ProgressStatus.IN_PROGRESS, ProgressStatus.IN_PROGRESS);
        assertConflict(ProgressStatus.DONE, ProgressStatus.DONE);
        assertConflict(ProgressStatus.SKIPPED, ProgressStatus.SKIPPED);
    }

    @Test
    void validate_whenNextStatusIsNull_throwsInvalidInput() {
        assertThatThrownBy(() -> ProgressTransitionValidator.validate(ProgressStatus.TODO, null))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    private void assertConflict(ProgressStatus currentStatus, ProgressStatus nextStatus) {
        assertThatThrownBy(() -> ProgressTransitionValidator.validate(currentStatus, nextStatus))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);
    }
}
