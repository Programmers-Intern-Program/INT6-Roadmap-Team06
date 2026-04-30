package com.back.coach.global.security.oauth2;

import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public record OAuth2State(String redirectUrl) {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.ALWAYS);

    public static String encode(String redirectUrl) {
        try {
            byte[] json = MAPPER.writeValueAsBytes(new OAuth2State(redirectUrl));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (JsonProcessingException e) {
            throw new ServiceException(ErrorCode.INVALID_OAUTH_STATE);
        }
    }

    public static OAuth2State decode(String token) {
        if (token == null || token.isBlank()) {
            throw new ServiceException(ErrorCode.INVALID_OAUTH_STATE);
        }
        try {
            byte[] json = Base64.getUrlDecoder().decode(token);
            return MAPPER.readValue(new String(json, StandardCharsets.UTF_8), OAuth2State.class);
        } catch (IllegalArgumentException | JsonProcessingException e) {
            throw new ServiceException(ErrorCode.INVALID_OAUTH_STATE);
        }
    }
}
