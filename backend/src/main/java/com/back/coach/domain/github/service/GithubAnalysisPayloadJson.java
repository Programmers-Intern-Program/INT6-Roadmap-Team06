package com.back.coach.domain.github.service;

import com.back.coach.domain.github.dto.GithubAnalysisPayload;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

// GithubAnalysisPayload ↔ JSON String 직렬화/역직렬화 헬퍼.
// NON_NULL: meta 등 선택 필드를 JSONB에서 생략. FAIL_ON_UNKNOWN_PROPERTIES=false: 포워드 호환.
@Component
public class GithubAnalysisPayloadJson {

    private final ObjectMapper mapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public String toJson(GithubAnalysisPayload payload) {
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("GithubAnalysisPayload 직렬화 실패", e);
        }
    }

    public GithubAnalysisPayload fromJson(String json) {
        try {
            return mapper.readValue(json, GithubAnalysisPayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("GithubAnalysisPayload 역직렬화 실패", e);
        }
    }
}
