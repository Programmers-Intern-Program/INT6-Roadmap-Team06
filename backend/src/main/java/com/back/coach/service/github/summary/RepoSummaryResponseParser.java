package com.back.coach.service.github.summary;

import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.back.coach.service.github.AnalysisPayload;
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
public class RepoSummaryResponseParser {

    private static final Logger log = LoggerFactory.getLogger(RepoSummaryResponseParser.class);
    private static final String SCHEMA_PATH = "ai/schema/repo-summary.schema.json";

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonSchema schema = loadSchema();

    public AnalysisPayload.RepoSummary parse(String llmJson) {
        JsonNode root;
        try {
            root = mapper.readTree(llmJson);
        } catch (IOException e) {
            log.warn("RepoSummary 응답 JSON 파싱 실패: {}", e.getMessage());
            throw new ServiceException(ErrorCode.LLM_INVALID_RESPONSE);
        }

        Set<ValidationMessage> errors = schema.validate(root);
        if (!errors.isEmpty()) {
            log.warn("RepoSummary 응답 schema 위반: {}", errors);
            throw new ServiceException(ErrorCode.LLM_INVALID_RESPONSE);
        }

        List<String> highlights = new ArrayList<>();
        root.get("highlights").forEach(n -> highlights.add(n.asText()));

        return new AnalysisPayload.RepoSummary(
                root.get("repoId").asText(),
                root.get("repoName").asText(),
                root.get("summary").asText(),
                highlights
        );
    }

    private static JsonSchema loadSchema() {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try (InputStream in = new ClassPathResource(SCHEMA_PATH).getInputStream()) {
            return factory.getSchema(in);
        } catch (IOException e) {
            throw new IllegalStateException("RepoSummary schema 로드 실패: " + SCHEMA_PATH, e);
        }
    }
}
