package com.back.coach.service.github.synthesis;

import com.back.coach.global.code.GithubDepthLevel;
import com.back.coach.global.code.GithubEvidenceType;
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
public class SynthesisResponseParser {

    private static final Logger log = LoggerFactory.getLogger(SynthesisResponseParser.class);
    private static final String SCHEMA_PATH = "ai/schema/synthesis.schema.json";

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonSchema schema = loadSchema();

    public SynthesisResult parse(String llmJson) {
        JsonNode root;
        try {
            root = mapper.readTree(llmJson);
        } catch (IOException e) {
            log.warn("Synthesis 응답 JSON 파싱 실패: {}", e.getMessage());
            throw new ServiceException(ErrorCode.LLM_INVALID_RESPONSE);
        }

        Set<ValidationMessage> errors = schema.validate(root);
        if (!errors.isEmpty()) {
            log.warn("Synthesis 응답 schema 위반: {}", errors);
            throw new ServiceException(ErrorCode.LLM_INVALID_RESPONSE);
        }

        return new SynthesisResult(
                mapList(root.get("techTags"), n -> new AnalysisPayload.TechTag(
                        n.get("skillName").asText(), n.get("tagReason").asText())),
                mapList(root.get("depthEstimates"), n -> new AnalysisPayload.DepthEstimate(
                        n.get("skillName").asText(),
                        GithubDepthLevel.valueOf(n.get("level").asText()),
                        n.get("reason").asText())),
                mapList(root.get("evidences"), n -> new AnalysisPayload.Evidence(
                        n.get("repoName").asText(),
                        GithubEvidenceType.valueOf(n.get("type").asText()),
                        n.get("source").asText(),
                        n.get("summary").asText())),
                new AnalysisPayload.FinalTechProfile(
                        mapList(root.get("finalTechProfile").get("confirmedSkills"), JsonNode::asText),
                        mapList(root.get("finalTechProfile").get("focusAreas"), JsonNode::asText))
        );
    }

    private static <T> List<T> mapList(JsonNode array, java.util.function.Function<JsonNode, T> mapper) {
        List<T> out = new ArrayList<>();
        if (array == null) return out;
        array.forEach(n -> out.add(mapper.apply(n)));
        return out;
    }

    private static JsonSchema loadSchema() {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try (InputStream in = new ClassPathResource(SCHEMA_PATH).getInputStream()) {
            return factory.getSchema(in);
        } catch (IOException e) {
            throw new IllegalStateException("Synthesis schema 로드 실패: " + SCHEMA_PATH, e);
        }
    }

    public record SynthesisResult(
            List<AnalysisPayload.TechTag> techTags,
            List<AnalysisPayload.DepthEstimate> depthEstimates,
            List<AnalysisPayload.Evidence> evidences,
            AnalysisPayload.FinalTechProfile finalTechProfile
    ) {}
}
