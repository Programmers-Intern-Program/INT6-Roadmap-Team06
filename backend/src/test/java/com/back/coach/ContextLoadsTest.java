package com.back.coach;

import com.back.coach.support.ApiTestBase;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 테스트 스캐폴드 스모크: Spring 컨텍스트가 뜨고 MockMvc 가 동작하는지만 확인.
 * 실패 시 Docker Desktop / application-test.yml / Testcontainers 설정 점검.
 */
class ContextLoadsTest extends ApiTestBase {

    @Test
    void contextLoadsAndMockMvcServesRequests() throws Exception {
        // 매핑되지 않은 경로라도 MockMvc 가 응답하면 컨텍스트는 정상.
        mockMvc.perform(get("/__nonexistent__"));
    }
}
