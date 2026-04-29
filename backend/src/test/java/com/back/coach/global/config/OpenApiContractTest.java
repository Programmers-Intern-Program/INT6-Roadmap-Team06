package com.back.coach.global.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiContractTest {

    @Test
    void openApiContract_hasV1ApiDocumentShape() throws IOException {
        Map<String, Object> document = loadOpenApiDocument();

        assertThat(document.get("openapi")).isEqualTo("3.0.3");
        assertThat(document).containsKeys("info", "paths", "components");

        Map<String, Object> info = mapValue(document, "info");
        assertThat(info.get("title")).isEqualTo("AI Developer Growth Coach API");
        assertThat(info.get("version")).isEqualTo("v1");
    }

    @Test
    void openApiContract_marksImplementationStatusForImplementedAndPlannedApis() throws IOException {
        Map<String, Object> paths = mapValue(loadOpenApiDocument(), "paths");

        assertThat(operation(paths, "/api/roadmaps/{roadmapId}/progress", "post")
                .get("x-implementation-status")).isEqualTo("implemented");
        assertThat(operation(paths, "/api/profiles", "post")
                .get("x-implementation-status")).isEqualTo("implemented");
        assertThat(operation(paths, "/api/profiles/me", "get")
                .get("x-implementation-status")).isEqualTo("implemented");
        assertThat(operation(paths, "/api/roadmaps/{roadmapId}", "get")
                .get("x-implementation-status")).isEqualTo("implemented");
        assertThat(operation(paths, "/api/dashboard", "get")
                .get("x-implementation-status")).isEqualTo("implemented");
        assertThat(operation(paths, "/api/diagnoses/{diagnosisId}", "get")
                .get("x-implementation-status")).isEqualTo("implemented");
        assertThat(operation(paths, "/api/github-analyses/{githubAnalysisId}", "get")
                .get("x-implementation-status")).isEqualTo("implemented");
        assertThat(operation(paths, "/api/github-analyses/{githubAnalysisId}/corrections", "patch")
                .get("x-implementation-status")).isEqualTo("implemented");
    }

    @Test
    void openApiContract_hasSharedResponseAndEnumSchemas() throws IOException {
        Map<String, Object> components = mapValue(loadOpenApiDocument(), "components");
        Map<String, Object> schemas = mapValue(components, "schemas");

        assertThat(schemas).containsKeys("ErrorResponse", "ProgressStatus", "CurrentLevel", "DashboardApiResponse");
        assertThat(mapValue(schemas, "ProgressStatus").get("enum"))
                .isEqualTo(List.of("TODO", "IN_PROGRESS", "DONE", "SKIPPED"));
        Map<String, Object> errorResponseProperties = mapValue(mapValue(schemas, "ErrorResponse"), "properties");
        assertThat(errorResponseProperties).containsKeys("code", "message", "details");
    }

    private Map<String, Object> loadOpenApiDocument() throws IOException {
        Path path = openApiPath();
        assertThat(path).exists();
        try (Reader reader = Files.newBufferedReader(path)) {
            return new Yaml().load(reader);
        }
    }

    private Path openApiPath() {
        Path backendWorkingDirectoryPath = Path.of("..", "docs", "openapi.yml").normalize();
        if (Files.exists(backendWorkingDirectoryPath)) {
            return backendWorkingDirectoryPath;
        }
        return Path.of("docs", "openapi.yml").normalize();
    }

    private Map<String, Object> operation(Map<String, Object> paths, String path, String method) {
        return mapValue(mapValue(paths, path), method);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Map<String, Object> source, String key) {
        return (Map<String, Object>) source.get(key);
    }
}
