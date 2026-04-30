package com.back.coach.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(SecurityConfigTest.TestConfig.class)
@TestPropertySource(properties = {
        "security.jwt.secret=test-secret-key-for-security-config",
        "security.jwt.access-ttl-seconds=900",
        "security.jwt.refresh-ttl-seconds=86400",
        "security.cors.allowed-origins=http://localhost:3000"
})
class SecurityConfigTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtTokenProvider tokenProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void protectedApi_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/profiles/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."));
    }

    @Test
    void protectedApi_withValidAccessToken_returnsAuthenticatedUser() throws Exception {
        String token = tokenProvider.createAccessToken(10L);

        mockMvc.perform(get("/api/v1/profiles/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("10"));
    }

    @Test
    void protectedApi_withInvalidToken_returnsInvalidTokenError() throws Exception {
        mockMvc.perform(get("/api/v1/profiles/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 토큰입니다."));
    }

    @Test
    void protectedApi_withAccessTokenCookie_returnsAuthenticatedUser() throws Exception {
        String token = tokenProvider.createAccessToken(11L);

        mockMvc.perform(get("/api/v1/profiles/me")
                        .cookie(new jakarta.servlet.http.Cookie("accessToken", token)))
                .andExpect(status().isOk())
                .andExpect(content().string("11"));
    }

    @Test
    void protectedApi_withInvalidAccessTokenCookie_returnsInvalidTokenError() throws Exception {
        mockMvc.perform(get("/api/v1/profiles/me")
                        .cookie(new jakarta.servlet.http.Cookie("accessToken", "junk")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"));
    }

    @Test
    void publicAuthApi_withoutToken_isPermitted() throws Exception {
        mockMvc.perform(get("/api/v1/auth/ping"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("ok"));
    }

    @Test
    void corsPreflight_fromLocalFrontend_isPermitted() throws Exception {
        mockMvc.perform(options("/api/v1/profiles/me")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PUT,PATCH,DELETE,OPTIONS"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @RestController
    static class TestApiController {

        @GetMapping(path = "/api/v1/profiles/me", produces = MediaType.TEXT_PLAIN_VALUE)
        String me(Authentication authentication) {
            AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
            return user.getName();
        }

        @GetMapping(path = "/api/v1/auth/ping", produces = MediaType.TEXT_PLAIN_VALUE)
        String ping() {
            return "ok";
        }
    }

    @Configuration
    @EnableWebMvc
    @Import({
            SecurityConfig.class,
            JwtAuthenticationFilter.class,
            JwtTokenProvider.class,
            JwtAuthenticationEntryPoint.class,
            JwtAccessDeniedHandler.class,
            SecurityErrorResponseWriter.class
    })
    static class TestConfig {

        @Bean
        TestApiController testApiController() {
            return new TestApiController();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
