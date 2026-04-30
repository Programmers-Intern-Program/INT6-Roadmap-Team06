package com.back.coach.global.security;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.SET_COOKIE;

class CookieManagerTest {

    @Test
    void add_emitsHttpOnlySameSiteLaxCookie_whenSecureFalseAndDomainBlank() {
        CookieManager cookies = new CookieManager(false, "");
        HttpServletResponse response = new MockHttpServletResponse();

        cookies.add(response, "accessToken", "abc.def.ghi", Duration.ofSeconds(900));

        String header = response.getHeader(SET_COOKIE);
        assertThat(header)
                .startsWith("accessToken=abc.def.ghi")
                .contains("Path=/")
                .contains("Max-Age=900")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
        assertThat(header).doesNotContain("Secure");
        assertThat(header).doesNotContain("Domain=");
    }

    @Test
    void add_includesSecureAndDomain_whenConfigured() {
        CookieManager cookies = new CookieManager(true, "example.com");
        HttpServletResponse response = new MockHttpServletResponse();

        cookies.add(response, "refreshToken", "rrr", Duration.ofSeconds(86400));

        String header = response.getHeader(SET_COOKIE);
        assertThat(header)
                .contains("Secure")
                .contains("Domain=example.com")
                .contains("Max-Age=86400");
    }

    @Test
    void clear_emitsExpiredCookieWithSameAttributes() {
        CookieManager cookies = new CookieManager(true, "example.com");
        HttpServletResponse response = new MockHttpServletResponse();

        cookies.clear(response, "accessToken");

        String header = response.getHeader(SET_COOKIE);
        assertThat(header)
                .startsWith("accessToken=")
                .contains("Max-Age=0")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .contains("Secure")
                .contains("Domain=example.com");
    }

    @Test
    void readValue_returnsCookieValueByName() {
        CookieManager cookies = new CookieManager(false, "");
        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest();
        request.setCookies(
                new jakarta.servlet.http.Cookie("accessToken", "tokenA"),
                new jakarta.servlet.http.Cookie("refreshToken", "tokenR")
        );

        assertThat(cookies.readValue(request, "accessToken")).isEqualTo("tokenA");
        assertThat(cookies.readValue(request, "refreshToken")).isEqualTo("tokenR");
        assertThat(cookies.readValue(request, "missing")).isNull();
    }

    @Test
    void readValue_returnsNull_whenNoCookies() {
        CookieManager cookies = new CookieManager(false, "");
        assertThat(cookies.readValue(new org.springframework.mock.web.MockHttpServletRequest(), "x")).isNull();
    }

    @Test
    void multipleAdds_appendDistinctSetCookieHeaders() {
        CookieManager cookies = new CookieManager(false, "");
        MockHttpServletResponse response = new MockHttpServletResponse();
        cookies.add(response, "a", "1", Duration.ofSeconds(60));
        cookies.add(response, "b", "2", Duration.ofSeconds(60));

        List<String> headers = response.getHeaders(SET_COOKIE);
        assertThat(headers).hasSize(2);
        assertThat(headers.get(0)).startsWith("a=1");
        assertThat(headers.get(1)).startsWith("b=2");
    }
}
