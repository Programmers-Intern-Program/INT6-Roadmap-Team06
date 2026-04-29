package com.back.coach.service.github;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class AnalysisPayloadJson {

    private final ObjectMapper mapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public String toJson(AnalysisPayload payload) {
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("AnalysisPayload 직렬화 실패", e);
        }
    }

    public AnalysisPayload fromJson(String json) {
        try {
            return mapper.readValue(json, AnalysisPayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("AnalysisPayload 역직렬화 실패", e);
        }
    }
}
