package com.back.coach.global.security.oauth2;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "spring.security.oauth2.client.registration.github", name = "client-id")
public class CoachOAuth2UserService extends DefaultOAuth2UserService {

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User user = super.loadUser(userRequest);
        // Validate up-front that the attributes can be mapped; surfaces as INVALID_INPUT
        // before the success handler runs.
        GithubUserInfoMapper.from(user);
        return user;
    }
}
