package com.back.coach.global.security.oauth2;

import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.back.coach.service.auth.GithubUserInfo;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Map;

public final class GithubUserInfoMapper {

    private GithubUserInfoMapper() {
    }

    public static GithubUserInfo from(OAuth2User user) {
        Map<String, Object> attrs = user.getAttributes();
        String providerUserId = stringify(attrs.get("id"));
        String login = stringify(attrs.get("login"));
        if (providerUserId == null) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "GitHub user id is missing");
        }
        if (login == null) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "GitHub login is missing");
        }
        String email = stringify(attrs.get("email"));
        return new GithubUserInfo(providerUserId, login, email);
    }

    private static String stringify(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }
}
