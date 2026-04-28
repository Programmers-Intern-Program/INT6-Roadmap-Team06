package com.back.coach.domain.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonbPayloadFixtureTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings = {
            "fixtures/jsonb/capability-diagnosis-payload.json",
            "fixtures/jsonb/github-analysis-payload.json",
            "fixtures/jsonb/learning-roadmap-payload.json",
            "fixtures/jsonb/roadmap-week-materials.json",
            "fixtures/jsonb/roadmap-week-tasks.json",
            "fixtures/jsonb/user-profile-interest-areas.json"
    })
    void jsonbFixtures_parseAsJson(String resourcePath) throws IOException {
        assertThat(readJson(resourcePath)).isNotNull();
    }

    @Test
    void capabilityDiagnosisPayload_matchesRequiredShape() throws IOException {
        JsonNode payload = readJson("fixtures/jsonb/capability-diagnosis-payload.json");

        assertObjectFields(payload, "missingSkills", "strengths", "recommendations", "githubInsights");
        assertArrayIsNotEmpty(payload.get("missingSkills"));
        assertObjectFields(payload.get("missingSkills").get(0), "skillName", "severity", "reason", "priorityOrder");
        assertThat(payload.get("missingSkills").get(0).get("priorityOrder").asInt()).isGreaterThanOrEqualTo(1);
        assertArrayIsNotEmpty(payload.get("strengths"));
        assertArrayIsNotEmpty(payload.get("recommendations"));
        assertObjectFields(payload.get("githubInsights"), "confirmedSkills", "newFromGithub");
    }

    @Test
    void githubAnalysisPayload_matchesRequiredShape() throws IOException {
        JsonNode payload = readJson("fixtures/jsonb/github-analysis-payload.json");

        assertObjectFields(
                payload,
                "staticSignals",
                "repoSummaries",
                "techTags",
                "depthEstimates",
                "evidences",
                "userCorrections",
                "finalTechProfile"
        );
        assertObjectFields(
                payload.get("staticSignals"),
                "primaryLanguages",
                "activeRepos",
                "commitFrequency",
                "contributionPattern"
        );
        assertArrayIsNotEmpty(payload.get("staticSignals").get("primaryLanguages"));
        assertObjectFields(payload.get("staticSignals").get("primaryLanguages").get(0), "lang", "ratio");
        assertArrayIsNotEmpty(payload.get("repoSummaries"));
        assertObjectFields(payload.get("repoSummaries").get(0), "repoId", "repoName", "summary", "highlights");
        assertArrayIsNotEmpty(payload.get("techTags"));
        assertObjectFields(payload.get("techTags").get(0), "skillName", "tagReason");
        assertArrayIsNotEmpty(payload.get("depthEstimates"));
        assertObjectFields(payload.get("depthEstimates").get(0), "skillName", "level", "reason");
        assertArrayIsNotEmpty(payload.get("evidences"));
        assertObjectFields(payload.get("evidences").get(0), "repoName", "type", "source", "summary");
        assertObjectFields(payload.get("finalTechProfile"), "confirmedSkills", "focusAreas");
    }

    @Test
    void learningRoadmapPayload_matchesRequiredShapeAndDoesNotContainProgressStatus() throws IOException {
        JsonNode payload = readJson("fixtures/jsonb/learning-roadmap-payload.json");

        assertObjectFields(payload, "weeks");
        assertArrayIsNotEmpty(payload.get("weeks"));
        JsonNode week = payload.get("weeks").get(0);
        assertObjectFields(week, "weekNumber", "topic", "reason", "tasks", "materials", "estimatedHours");
        assertArrayIsNotEmpty(week.get("tasks"));
        assertObjectFields(week.get("tasks").get(0), "type", "title");
        assertArrayIsNotEmpty(week.get("materials"));
        assertObjectFields(week.get("materials").get(0), "type", "title", "url");
        assertThat(containsFieldName(payload, "progressStatus")).isFalse();
        assertThat(containsFieldName(payload, "progressNote")).isFalse();
        assertThat(containsFieldName(payload, "progressUpdatedAt")).isFalse();
    }

    @Test
    void roadmapWeekTasksAndMaterials_matchRequiredShape() throws IOException {
        JsonNode tasks = readJson("fixtures/jsonb/roadmap-week-tasks.json");
        JsonNode materials = readJson("fixtures/jsonb/roadmap-week-materials.json");

        assertArrayIsNotEmpty(tasks);
        assertObjectFields(tasks.get(0), "type", "title");
        assertArrayIsNotEmpty(materials);
        assertObjectFields(materials.get(0), "type", "title", "url");
    }

    @Test
    void userProfileInterestAreas_matchRequiredShape() throws IOException {
        JsonNode interestAreas = readJson("fixtures/jsonb/user-profile-interest-areas.json");

        assertArrayIsNotEmpty(interestAreas);
        for (JsonNode interestArea : interestAreas) {
            assertThat(interestArea.isTextual()).isTrue();
        }
    }

    private JsonNode readJson(String resourcePath) throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(inputStream).as(resourcePath).isNotNull();
            return objectMapper.readTree(inputStream);
        }
    }

    private void assertObjectFields(JsonNode node, String... expectedFieldNames) {
        assertThat(node).isNotNull();
        assertThat(node.isObject()).isTrue();
        assertThat(fieldNames(node)).contains(expectedFieldNames);
    }

    private void assertArrayIsNotEmpty(JsonNode node) {
        assertThat(node).isNotNull();
        assertThat(node.isArray()).isTrue();
        assertThat(node.size()).isPositive();
    }

    private List<String> fieldNames(JsonNode node) {
        List<String> fieldNames = new ArrayList<>();
        node.fieldNames().forEachRemaining(fieldNames::add);
        return fieldNames;
    }

    private boolean containsFieldName(JsonNode node, String fieldName) {
        if (node.isObject() && node.has(fieldName)) {
            return true;
        }

        Iterator<JsonNode> elements = node.elements();
        while (elements.hasNext()) {
            if (containsFieldName(elements.next(), fieldName)) {
                return true;
            }
        }
        return false;
    }
}
