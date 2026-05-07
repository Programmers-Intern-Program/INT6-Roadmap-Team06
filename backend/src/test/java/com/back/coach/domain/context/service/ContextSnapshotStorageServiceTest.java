package com.back.coach.domain.context.service;

import com.back.coach.domain.context.entity.UserContextSnapshot;
import com.back.coach.domain.context.repository.UserContextSnapshotRepository;
import com.back.coach.global.code.ContextType;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ContextSnapshotStorageServiceTest {

    @Mock
    private UserContextSnapshotRepository userContextSnapshotRepository;

    private ContextSnapshotStorageService contextSnapshotStorageService;

    @BeforeEach
    void setUp() {
        contextSnapshotStorageService = new ContextSnapshotStorageService(
                userContextSnapshotRepository,
                new ObjectMapper()
        );
    }

    @Test
    void createSnapshot_whenNoPreviousSnapshot_startsWithVersionOne() {
        given(userContextSnapshotRepository.findActiveByUserIdAndContextType(1L, ContextType.PROFILE))
                .willReturn(Optional.empty());
        given(userContextSnapshotRepository.findMaxVersionByUserIdAndContextType(1L, ContextType.PROFILE))
                .willReturn(null);
        given(userContextSnapshotRepository.save(any(UserContextSnapshot.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        UserContextSnapshot snapshot = contextSnapshotStorageService.createSnapshot(
                1L,
                ContextType.PROFILE,
                payload(ContextType.PROFILE)
        );

        assertThat(snapshot.getUserId()).isEqualTo(1L);
        assertThat(snapshot.getContextType()).isEqualTo(ContextType.PROFILE);
        assertThat(snapshot.getVersion()).isEqualTo(1);
        assertThat(snapshot.getValidFrom()).isNotNull();
        assertThat(snapshot.getValidTo()).isNull();
    }

    @Test
    void createSnapshot_whenActiveSnapshotExists_closesPreviousAndCreatesNextVersion() {
        UserContextSnapshot activeSnapshot = UserContextSnapshot.create(
                1L,
                ContextType.PROFILE,
                1,
                payload(ContextType.PROFILE),
                Instant.parse("2026-05-06T00:00:00Z")
        );
        given(userContextSnapshotRepository.findActiveByUserIdAndContextType(1L, ContextType.PROFILE))
                .willReturn(Optional.of(activeSnapshot));
        given(userContextSnapshotRepository.findMaxVersionByUserIdAndContextType(1L, ContextType.PROFILE))
                .willReturn(1);
        given(userContextSnapshotRepository.save(any(UserContextSnapshot.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        UserContextSnapshot snapshot = contextSnapshotStorageService.createSnapshot(
                1L,
                ContextType.PROFILE,
                payload(ContextType.PROFILE)
        );

        assertThat(snapshot.getVersion()).isEqualTo(2);
        assertThat(snapshot.getValidTo()).isNull();
        assertThat(activeSnapshot.getValidTo()).isEqualTo(snapshot.getValidFrom());
    }

    @Test
    void createSnapshot_whenPayloadContextTypeMismatch_throwsInvalidInput() {
        assertThatThrownBy(() -> contextSnapshotStorageService.createSnapshot(
                1L,
                ContextType.PROFILE,
                payload(ContextType.PLAN)
        )).isInstanceOfSatisfying(ServiceException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));

        then(userContextSnapshotRepository).shouldHaveNoInteractions();
    }

    @Test
    void createSnapshot_whenPayloadSourceRefsMissing_throwsInvalidInput() {
        String payload = """
                {
                  "contextType": "PROFILE",
                  "generatedAt": "2026-05-07T00:00:00Z"
                }
                """;

        assertThatThrownBy(() -> contextSnapshotStorageService.createSnapshot(
                1L,
                ContextType.PROFILE,
                payload
        )).isInstanceOfSatisfying(ServiceException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));

        then(userContextSnapshotRepository).shouldHaveNoInteractions();
    }

    private String payload(ContextType contextType) {
        return """
                {
                  "contextType": "%s",
                  "generatedAt": "2026-05-07T00:00:00Z",
                  "sourceRefs": {
                    "profileId": "1"
                  }
                }
                """.formatted(contextType.code());
    }
}
