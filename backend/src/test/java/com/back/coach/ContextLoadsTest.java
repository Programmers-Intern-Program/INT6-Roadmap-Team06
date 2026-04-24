package com.back.coach;

import com.back.coach.global.logging.TraceIdFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 경량 스모크: MockMvc 와 TraceIdFilter 가 빠르게 동작하는지만 확인.
 */
class ContextLoadsTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new TestController())
            .addFilters(new TraceIdFilter())
            .build();

    @Test
    @DisplayName("MockMvc 와 TraceId 필터가 빠르게 동작한다")
    void mockMvcAndTraceIdFilterWork() throws Exception {
        mockMvc.perform(get("/__test__/ping"))
                .andExpect(status().isOk())
                .andExpect(header().exists(TraceIdFilter.HEADER))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("ok"));
    }

    @RestController
    public static class TestController {

        @GetMapping(path = "/__test__/ping", produces = MediaType.TEXT_PLAIN_VALUE)
        String ping() {
            return "ok";
        }
    }
}
