package com.back.coach.service.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RepoMetadataJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("github_projects.metadata_payload JSONB 전체 shape를 RepoMetadata로 역직렬화한다")
    void deserialize_fullPayload() throws Exception {
        String jsonb = """
                {
                  "readmeExcerpt": "# my-cool-app\\nA Spring Boot service.",
                  "languageBytes": { "Java": 12345, "TypeScript": 6789 },
                  "dependencyFiles": [
                    { "path": "pom.xml", "contentExcerpt": "<project>...</project>" },
                    { "path": "package.json", "contentExcerpt": "{\\"name\\":\\"front\\"}" }
                  ],
                  "commits": [
                    {
                      "sha": "abc123",
                      "subject": "feat: add OAuth handler",
                      "bodyExcerpt": "Implements GitHub OAuth2 flow",
                      "paths": ["src/main/java/.../OAuth2Handler.java"],
                      "additions": 120,
                      "deletions": 5,
                      "diffExcerpt": "@@ -1,3 +1,5 @@ ..."
                    }
                  ],
                  "pullRequests": [
                    { "number": 42, "title": "Add OAuth", "bodyExcerpt": "...", "state": "MERGED", "additions": 200, "deletions": 10 }
                  ],
                  "issues": [
                    { "number": 7, "title": "Login flow broken", "bodyExcerpt": "Steps to reproduce...", "state": "CLOSED", "commentExcerpts": ["I'll take this", "Fixed in #42"] }
                  ]
                }
                """;

        RepoMetadata metadata = mapper.readValue(jsonb, RepoMetadata.class);

        assertThat(metadata.readmeExcerpt()).startsWith("# my-cool-app");
        assertThat(metadata.languageBytes()).containsEntry("Java", 12345L).containsEntry("TypeScript", 6789L);
        assertThat(metadata.dependencyFiles()).hasSize(2);
        assertThat(metadata.dependencyFiles().get(0).path()).isEqualTo("pom.xml");
        assertThat(metadata.commits()).hasSize(1);
        assertThat(metadata.commits().get(0).sha()).isEqualTo("abc123");
        assertThat(metadata.commits().get(0).additions()).isEqualTo(120);
        assertThat(metadata.pullRequests()).hasSize(1);
        assertThat(metadata.pullRequests().get(0).number()).isEqualTo(42);
        assertThat(metadata.issues()).hasSize(1);
        assertThat(metadata.issues().get(0).commentExcerpts()).hasSize(2);
    }

    @Test
    @DisplayName("선택적 필드가 누락되어도 안전하게 빈 컬렉션/null로 채운다 (Slice 3 fetcher가 부분 채움 가능)")
    void deserialize_missingOptionalFields_yieldsEmpties() throws Exception {
        // README/dep 파일이 없는 repo도 fetcher가 만날 수 있음
        String jsonb = """
                {
                  "languageBytes": {},
                  "commits": [],
                  "pullRequests": [],
                  "issues": []
                }
                """;

        RepoMetadata metadata = mapper.readValue(jsonb, RepoMetadata.class);

        assertThat(metadata.readmeExcerpt()).isNull();
        assertThat(metadata.languageBytes()).isEmpty();
        assertThat(metadata.dependencyFiles()).isNullOrEmpty();
        assertThat(metadata.commits()).isEmpty();
    }
}
