package com.back.coach.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * API(HTTP 계층) 테스트 베이스.
 *
 * <p>{@link IntegrationTest} 설정을 포함한다 — 별도 어노테이션 불필요.
 *
 * <h2>사용 예 (HTTP 레이어만 검증)</h2>
 * <pre>{@code
 * class UserApiTest extends ApiTestBase {
 *     @MockitoBean private UserService userService;
 *
 *     @Test
 *     void getMe_returns401WhenUnauthenticated() throws Exception {
 *         mockMvc.perform(get("/api/v1/users/me"))
 *                .andExpect(status().isUnauthorized());
 *     }
 * }
 * }</pre>
 *
 * <p><b>참고:</b>
 * <ul>
 *   <li>{@code @MockitoBean} 이 선언된 테스트는 mock 이 없는 통합 테스트와 별도의 컨텍스트를 쓴다.
 *       {@code ApiTestBase} 를 상속한 테스트끼리는 공유한다.</li>
 *   <li>외부 API stub 이 필요하면 WireMock 을 {@code @RegisterExtension} 으로 클래스에 직접 추가한다 (build.gradle 에 이미 있음).</li>
 *   <li>SecurityConfig 가 STATELESS 라 {@code @AutoConfigureMockMvc} 만으로는 {@code @WithMockUser} 가 동작하지 않는다.
 *       그래서 {@code springSecurity()} 를 명시적으로 apply 한다.</li>
 * </ul>
 */
@IntegrationTest
public abstract class ApiTestBase {

    @Autowired
    private WebApplicationContext context;

    protected MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }
}
