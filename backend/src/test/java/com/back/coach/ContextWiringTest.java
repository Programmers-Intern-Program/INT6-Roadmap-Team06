package com.back.coach;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("wiring-test")
@Tag("pr-gate")
class ContextWiringTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("Docker 없이 애플리케이션 컨텍스트 wiring이 완료된다")
    void contextLoadsWithoutDocker() {
        assertThat(applicationContext).isNotNull();
    }
}
