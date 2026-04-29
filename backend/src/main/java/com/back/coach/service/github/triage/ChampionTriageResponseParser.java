package com.back.coach.service.github.triage;

import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.back.coach.service.github.Champion;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Triage LLM 응답을 schema 검증 후 List<Champion>으로 변환.
// 어떤 violation도 LLM_INVALID_RESPONSE로 통일 — 호출자가 fallback 결정.
@Component
public class ChampionTriageResponseParser {

    private static final Logger log = LoggerFactory.getLogger(ChampionTriageResponseParser.class);
    private static final String SCHEMA_PATH = "ai/schema/champion-triage.schema.json";

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonSchema schema = loadSchema();

    public List<Champion> parse(String llmJson) {
        JsonNode root;
        try {
            root = mapper.readTree(llmJson);
        } catch (IOException e) {
            log.warn("Triage 응답 JSON 파싱 실패: {}", e.getMessage());
            throw new ServiceException(ErrorCode.LLM_INVALID_RESPONSE);
        }

        Set<ValidationMessage> errors = schema.validate(root);
        if (!errors.isEmpty()) {
            log.warn("Triage 응답 schema 위반: {}", errors);
            throw new ServiceException(ErrorCode.LLM_INVALID_RESPONSE);
        }

        List<Champion> champions = new ArrayList<>();
        for (JsonNode item : root.get("champions")) {
            champions.add(new Champion(
                    Champion.Kind.valueOf(item.get("kind").asText()),
                    item.get("ref").asText(),
                    item.get("reason").asText()
            ));
        }
        return champions;
    }

    private static JsonSchema loadSchema() {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try (InputStream in = new ClassPathResource(SCHEMA_PATH).getInputStream()) {
            return factory.getSchema(in);
        } catch (IOException e) {
            throw new IllegalStateException("Champion triage schema 로드 실패: " + SCHEMA_PATH, e);
        }
    }
}
