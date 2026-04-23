package com.back.coach.support;

import com.back.coach.config.TestcontainersConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Repository / Schema 통합 테스트용 메타 어노테이션.
 *
 * <p>SpringBootTest + test 프로파일 + Testcontainers PostgreSQL/Redis 를 한 번에 적용한다.
 * API 테스트(MockMvc 필요)는 {@link ApiTestBase} 를 상속.
 *
 * <ul>
 *   <li><b>Docker Desktop 이 켜져 있어야 한다.</b> Testcontainers 가 컨테이너를 띄운다.
 *       꺼진 상태에서 실행하면 {@code IllegalStateException}.</li>
 *   <li><b>컨텍스트 캐시:</b> 같은 설정을 가진 테스트끼리는 Spring 컨텍스트를 공유한다.
 *       클래스마다 다른 {@code @ActiveProfiles} 또는 {@code @Import} 를 붙이면 캐시가 깨져 느려진다.</li>
 *   <li><b>단위 테스트에는 사용하지 마라.</b> 순수 로직은 {@code @ExtendWith(MockitoExtension.class)} 만으로 충분하다.</li>
 * </ul>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {
}
