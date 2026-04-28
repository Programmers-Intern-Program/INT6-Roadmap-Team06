package com.back.coach.api.auth;

import com.back.coach.domain.user.entity.User;
import com.back.coach.domain.user.repository.UserRepository;
import com.back.coach.global.code.AuthProvider;
import com.back.coach.global.security.JwtTokenProvider;
import com.back.coach.support.ApiTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest extends ApiTestBase {

    @Autowired
    UserRepository userRepository;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("GET /api/v1/auth/me — accessToken 쿠키로 인증된 사용자의 정보를 반환한다")
    void me_returnsCurrentUser() throws Exception {
        User user = userRepository.save(
                User.signupFromOAuth(AuthProvider.GITHUB, "gh-me-1", "me@example.com")
        );
        String accessToken = jwtTokenProvider.createAccessToken(user.getId());

        mockMvc.perform(get("/api/v1/auth/me").cookie(new Cookie("accessToken", accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.getId()))
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.authProvider").value("GITHUB"));
    }

    @Test
    @DisplayName("GET /api/v1/auth/me — 인증 없이 호출하면 401")
    void me_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh — 유효한 refreshToken 쿠키로 새 accessToken/refreshToken 쿠키를 발급한다")
    void refresh_rotatesTokens() throws Exception {
        User user = userRepository.save(
                User.signupFromOAuth(AuthProvider.GITHUB, "gh-refresh-1", "refresh@example.com")
        );
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", refreshToken)))
                .andExpect(status().isNoContent())
                .andReturn();

        Cookie newAccess = result.getResponse().getCookie("accessToken");
        Cookie newRefresh = result.getResponse().getCookie("refreshToken");
        assertThat(newAccess).isNotNull();
        assertThat(newAccess.getValue()).isNotBlank();
        assertThat(newAccess.isHttpOnly()).isTrue();
        assertThat(jwtTokenProvider.parseAccessToken(newAccess.getValue()).userId()).isEqualTo(user.getId());

        assertThat(newRefresh).isNotNull();
        assertThat(newRefresh.getValue()).isNotBlank();
        assertThat(jwtTokenProvider.parseRefreshToken(newRefresh.getValue()).userId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh — refreshToken 쿠키가 없으면 401")
    void refresh_withoutCookie_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh — accessToken을 refreshToken 자리에 보내면 401")
    void refresh_withAccessTokenCookie_returnsUnauthorized() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken(99L);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", accessToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout — accessToken/refreshToken 쿠키를 만료시킨다")
    void logout_clearsCookies() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent())
                .andReturn();

        Cookie cleared = result.getResponse().getCookie("accessToken");
        Cookie clearedRefresh = result.getResponse().getCookie("refreshToken");
        assertThat(cleared).isNotNull();
        assertThat(cleared.getMaxAge()).isZero();
        assertThat(clearedRefresh).isNotNull();
        assertThat(clearedRefresh.getMaxAge()).isZero();
    }
}
