package com.back.coach.domain.coach.service;

import com.back.coach.external.llm.LlmJsonResponseExtractor;
import com.back.coach.global.code.CoachRoute;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

@Component
public class CoachResponseParser {

    private static final Logger log = LoggerFactory.getLogger(CoachResponseParser.class);
    private static final String SCHEMA_PATH = "ai/schema/coach-response.schema.json";

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonSchema schema = loadSchema();

    public record ParsedCoachResponse(
            CoachRoute route,
            String responseText,
            String replanReason,
            String detectedIntent
    ) {}

    public ParsedCoachResponse parse(String llmJson) {
        JsonNode root;
        try {
            root = mapper.readTree(LlmJsonResponseExtractor.extractJson(llmJson));
        } catch (IOException ex) {
            log.warn("Coach 응답 JSON 파싱 실패: {}", ex.getMessage());
            throw new ServiceException(ErrorCode.LLM_INVALID_RESPONSE);
        }

        Set<ValidationMessage> errors = schema.validate(root);
        if (!errors.isEmpty()) {
            log.warn("Coach 응답 schema 위반: {}", errors);
            throw new ServiceException(ErrorCode.LLM_INVALID_RESPONSE);
        }

        CoachRoute route = CoachRoute.valueOf(root.get("route").asText());
        String responseText = root.get("responseText").asText();
        String replanReason = root.has("replanReason") && !root.get("replanReason").isNull()
                ? root.get("replanReason").asText() : null;
        String detectedIntent = root.has("detectedIntent") && !root.get("detectedIntent").isNull()
                ? root.get("detectedIntent").asText() : null;

        return new ParsedCoachResponse(route, responseText, replanReason, detectedIntent);
    }

    private JsonSchema loadSchema() {
        try (InputStream is = new ClassPathResource(SCHEMA_PATH).getInputStream()) {
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(is);
        } catch (IOException e) {
            throw new IllegalStateException("coach-response schema 로드 실패", e);
        }
    }
}
