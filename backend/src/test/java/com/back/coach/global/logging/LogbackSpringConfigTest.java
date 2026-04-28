package com.back.coach.global.logging;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class LogbackSpringConfigTest {

    @Test
    void logbackSpringXml_consolePatternIncludesTraceIdMdc() {
        assertThatCode(() -> {
            try (InputStream input = getClass().getClassLoader().getResourceAsStream("logback-spring.xml")) {
                assertThat(input).isNotNull();

                String config = new String(input.readAllBytes(), StandardCharsets.UTF_8);

                assertThat(config).contains("CONSOLE_LOG_PATTERN");
                assertThat(config).contains("traceId=%X{traceId:-none}");
            }
        }).doesNotThrowAnyException();
    }
}
