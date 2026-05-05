package com.back.coach.domain.diagnosis.service;

import com.back.coach.domain.diagnosis.dto.DiagnosisPayload;
import com.back.coach.external.llm.LlmJsonResponseExtractor;
import com.back.coach.global.code.DiagnosisSeverity;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class DiagnosisResponseParser {

    private static final Logger log = LoggerFactory.getLogger(DiagnosisResponseParser.class);
    private static final String SCHEMA_PATH = "ai/schema/diagnosis.schema.json";

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonSchema schema = loadSchema();

    public DiagnosisResult parse(String llmJson) {
        JsonNode root;
        try {
            root = mapper.readTree(LlmJsonResponseExtractor.extractJson(llmJson));
        } catch (IOException ex) {
            log.warn("Diagnosis 응답 JSON 파싱 실패: {}", ex.getMessage());
            throw new ServiceException(ErrorCode.LLM_INVALID_RESPONSE);
        }

        Set<ValidationMessage> errors = schema.validate(root);
        if (!errors.isEmpty()) {
            log.warn("Diagnosis 응답 schema 위반: {}", errors);
            throw new ServiceException(ErrorCode.LLM_INVALID_RESPONSE);
        }

        return new DiagnosisResult(
                root.get("summary").asText(),
                mapList(root.get("missingSkills"), node -> new DiagnosisPayload.MissingSkill(
                        node.get("skillName").asText(),
                        DiagnosisSeverity.valueOf(node.get("severity").asText()),
                        node.get("reason").asText(),
                        node.get("priorityOrder").asInt()
                )),
                mapList(root.get("strengths"), JsonNode::asText),
                mapList(root.get("recommendations"), JsonNode::asText)
        );
    }

    private static <T> List<T> mapList(JsonNode array, java.util.function.Function<JsonNode, T> mapper) {
        List<T> values = new ArrayList<>();
        array.forEach(node -> values.add(mapper.apply(node)));
        return values;
    }

    private static JsonSchema loadSchema() {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try (InputStream inputStream = new ClassPathResource(SCHEMA_PATH).getInputStream()) {
            return factory.getSchema(inputStream);
        } catch (IOException ex) {
            throw new IllegalStateException("Diagnosis schema 로드 실패: " + SCHEMA_PATH, ex);
        }
    }

    public record DiagnosisResult(
            String summary,
            List<DiagnosisPayload.MissingSkill> missingSkills,
            List<String> strengths,
            List<String> recommendations
    ) {
    }
}
