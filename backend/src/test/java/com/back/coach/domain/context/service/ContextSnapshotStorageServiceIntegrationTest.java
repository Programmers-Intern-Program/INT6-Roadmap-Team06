package com.back.coach.domain.context.service;

import com.back.coach.domain.context.entity.UserContextSnapshot;
import com.back.coach.domain.context.repository.UserContextSnapshotRepository;
import com.back.coach.domain.user.entity.User;
import com.back.coach.domain.user.repository.UserRepository;
import com.back.coach.global.code.AuthProvider;
import com.back.coach.global.code.ContextType;
import com.back.coach.support.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class ContextSnapshotStorageServiceIntegrationTest {

    @Autowired
    private ContextSnapshotStorageService contextSnapshotStorageService;

    @Autowired
    private UserContextSnapshotRepository userContextSnapshotRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Context Snapshot은 context_type별 active/latest 기준으로 조회된다")
    void contextSnapshotActiveAndLatestAreScopedByContextType() {
        Long userId = createUser();

        UserContextSnapshot profileV1 = contextSnapshotStorageService.createSnapshot(
                userId,
                ContextType.PROFILE,
                payload(ContextType.PROFILE, """
                        {
                          "profileId": "1",
                          "diagnosisVersion": 1
                        }
                        """)
        );
        UserContextSnapshot planV1 = contextSnapshotStorageService.createSnapshot(
                userId,
                ContextType.PLAN,
                payload(ContextType.PLAN, """
                        {
                          "roadmapId": "7",
                          "roadmapVersion": 1
                        }
                        """)
        );
        UserContextSnapshot profileV2 = contextSnapshotStorageService.createSnapshot(
                userId,
                ContextType.PROFILE,
                payload(ContextType.PROFILE, """
                        {
                          "profileId": "2",
                          "diagnosisVersion": 2
                        }
                        """)
        );

        assertThat(contextSnapshotStorageService.findActiveSnapshot(userId, ContextType.PROFILE))
                .hasValueSatisfying(snapshot -> {
                    assertThat(snapshot.getId()).isEqualTo(profileV2.getId());
                    assertThat(snapshot.getVersion()).isEqualTo(2);
                    assertThat(snapshot.getValidTo()).isNull();
                });
        assertThat(contextSnapshotStorageService.findLatestSnapshot(userId, ContextType.PROFILE))
                .hasValueSatisfying(snapshot -> assertThat(snapshot.getId()).isEqualTo(profileV2.getId()));
        assertThat(contextSnapshotStorageService.findActiveSnapshot(userId, ContextType.PLAN))
                .hasValueSatisfying(snapshot -> {
                    assertThat(snapshot.getId()).isEqualTo(planV1.getId());
                    assertThat(snapshot.getVersion()).isEqualTo(1);
                    assertThat(snapshot.getValidTo()).isNull();
                });

        UserContextSnapshot closedProfile = userContextSnapshotRepository.findById(profileV1.getId()).orElseThrow();
        assertThat(closedProfile.getValidTo()).isNotNull();
    }

    @Test
    @DisplayName("Context Snapshot payload는 v1 원본 참조를 sourceRefs에 보존한다")
    void contextSnapshotPayloadPreservesSourceRefs() throws Exception {
        Long userId = createUser();

        UserContextSnapshot saved = contextSnapshotStorageService.createSnapshot(
                userId,
                ContextType.PROFILE,
                payload(ContextType.PROFILE, """
                        {
                          "profileId": "10",
                          "githubAnalysisId": "20",
                          "githubAnalysisVersion": 3,
                          "diagnosisId": "30",
                          "diagnosisVersion": 4
                        }
                        """)
        );

        UserContextSnapshot reloaded = userContextSnapshotRepository.findById(saved.getId()).orElseThrow();

        assertThat(objectMapper.readTree(reloaded.getPayload()).path("sourceRefs").path("profileId").asText())
                .isEqualTo("10");
        assertThat(objectMapper.readTree(reloaded.getPayload()).path("sourceRefs").path("githubAnalysisVersion").asInt())
                .isEqualTo(3);
        assertThat(objectMapper.readTree(reloaded.getPayload()).path("sourceRefs").path("diagnosisVersion").asInt())
                .isEqualTo(4);
    }

    private Long createUser() {
        String key = UUID.randomUUID().toString();
        User user = userRepository.save(
                User.signupFromOAuth(AuthProvider.GITHUB, "context-" + key, "context-" + key + "@example.com")
        );
        return user.getId();
    }

    private String payload(ContextType contextType, String sourceRefs) {
        return """
                {
                  "contextType": "%s",
                  "generatedAt": "2026-05-07T00:00:00Z",
                  "sourceRefs": %s
                }
                """.formatted(contextType.code(), sourceRefs);
    }
}
