package com.back.coach;

import com.back.coach.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Tag("pr-gate")
class ApplicationIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("Testcontainers 환경에서 전체 애플리케이션 컨텍스트가 뜬다")
    void contextLoadsWithTestcontainers() {
        assertThat(applicationContext).isNotNull();
    }
}
