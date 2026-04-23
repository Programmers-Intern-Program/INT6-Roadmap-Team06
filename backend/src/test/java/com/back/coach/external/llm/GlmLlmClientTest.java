package com.back.coach.external.llm;

import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GlmLlmClient 의 예외 매핑 골격 검증.
 * 실제 SDK 호출은 첫 사용 사례에서 wiring 되며, 그때 통합 테스트가 추가된다.
 */
class GlmLlmClientTest {

    private final GlmLlmClient client = new GlmLlmClient("dummy", "http://localhost:0", "glm-4-flash");

    @Test
    void blankPrompt_throwsInvalidInput() {
        assertThatThrownBy(() -> client.complete(""))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);

        assertThatThrownBy(() -> client.complete(null))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void unwiredCall_throwsAnalysisFailed() {
        // 골격 단계: 실제 SDK 호출 미구현 → ANALYSIS_FAILED 로 변환됨을 확인.
        assertThatThrownBy(() -> client.complete("hello"))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> {
                    ServiceException se = (ServiceException) e;
                    assertThat(se.getErrorCode()).isEqualTo(ErrorCode.ANALYSIS_FAILED);
                });
    }
}
