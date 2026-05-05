package com.back.coach.domain.roadmap.service;

import com.back.coach.domain.roadmap.dto.RoadmapPayload;
import com.back.coach.external.llm.LlmJsonResponseExtractor;
import com.back.coach.global.code.MaterialType;
import com.back.coach.global.code.RoadmapTaskType;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class RoadmapResponseParser {

    private static final Logger log = LoggerFactory.getLogger(RoadmapResponseParser.class);
    private static final String SCHEMA_PATH = "ai/schema/roadmap.schema.json";

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonSchema schema = loadSchema();

    public RoadmapResult parse(String llmJson) {
        JsonNode root;
        try {
            root = mapper.readTree(LlmJsonResponseExtractor.extractJson(llmJson));
        } catch (IOException ex) {
            log.warn("Roadmap 응답 JSON 파싱 실패: {}", ex.getMessage());
            throw new ServiceException(ErrorCode.LLM_INVALID_RESPONSE);
        }

        Set<ValidationMessage> errors = schema.validate(root);
        if (!errors.isEmpty()) {
            log.warn("Roadmap 응답 schema 위반: {}", errors);
            throw new ServiceException(ErrorCode.LLM_INVALID_RESPONSE);
        }

        List<RoadmapPayload.Week> weeks = mapWeeks(root.get("weeks"));
        validateWeekSequence(weeks);

        return new RoadmapResult(
                root.get("summary").asText(),
                weeks.stream()
                        .sorted(Comparator.comparing(RoadmapPayload.Week::weekNumber))
                        .toList()
        );
    }

    private List<RoadmapPayload.Week> mapWeeks(JsonNode weeksNode) {
        List<RoadmapPayload.Week> weeks = new ArrayList<>();
        weeksNode.forEach(node -> weeks.add(new RoadmapPayload.Week(
                node.get("weekNumber").asInt(),
                node.get("topic").asText(),
                node.get("reason").asText(),
                mapList(node.get("tasks"), task -> new RoadmapPayload.Task(
                        RoadmapTaskType.valueOf(task.get("type").asText()),
                        task.get("title").asText()
                )),
                mapList(node.get("materials"), material -> new RoadmapPayload.Material(
                        MaterialType.valueOf(material.get("type").asText()),
                        material.get("title").asText(),
                        material.hasNonNull("url") ? material.get("url").asText() : null
                )),
                node.get("estimatedHours").decimalValue()
        )));
        return weeks;
    }

    private void validateWeekSequence(List<RoadmapPayload.Week> weeks) {
        Set<Integer> weekNumbers = new HashSet<>();
        for (RoadmapPayload.Week week : weeks) {
            if (!weekNumbers.add(week.weekNumber())) {
                log.warn("Roadmap 응답 weekNumber 중복: {}", week.weekNumber());
                throw new ServiceException(ErrorCode.LLM_INVALID_RESPONSE);
            }
        }
        List<Integer> sortedWeekNumbers = weekNumbers.stream().sorted().toList();
        for (int i = 0; i < sortedWeekNumbers.size(); i++) {
            if (sortedWeekNumbers.get(i) != i + 1) {
                log.warn("Roadmap 응답 weekNumber 순서 위반: {}", sortedWeekNumbers);
                throw new ServiceException(ErrorCode.LLM_INVALID_RESPONSE);
            }
        }
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
            throw new IllegalStateException("Roadmap schema 로드 실패: " + SCHEMA_PATH, ex);
        }
    }

    public record RoadmapResult(
            String summary,
            List<RoadmapPayload.Week> weeks
    ) {
    }
}
