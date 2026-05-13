package com.back.coach.domain.roadmap.service;

import com.back.coach.domain.diagnosis.dto.DiagnosisPayload;
import com.back.coach.domain.diagnosis.entity.CapabilityDiagnosis;
import com.back.coach.domain.diagnosis.repository.CapabilityDiagnosisRepository;
import com.back.coach.domain.github.repository.GithubAnalysisRepository;
import com.back.coach.domain.jobrole.entity.JobRole;
import com.back.coach.domain.jobrole.repository.JobRoleRepository;
import com.back.coach.domain.result.service.ResultVersionService;
import com.back.coach.domain.roadmap.dto.RoadmapDetailResponse;
import com.back.coach.domain.roadmap.dto.RoadmapPayload;
import com.back.coach.domain.roadmap.dto.RoadmapRequest;
import com.back.coach.domain.roadmap.entity.LearningRoadmap;
import com.back.coach.domain.roadmap.entity.RoadmapWeek;
import com.back.coach.domain.roadmap.repository.LearningRoadmapRepository;
import com.back.coach.domain.roadmap.repository.RoadmapWeekRepository;
import com.back.coach.domain.user.entity.UserProfile;
import com.back.coach.domain.user.repository.UserProfileRepository;
import com.back.coach.external.llm.LlmClient;
import com.back.coach.global.code.CurrentLevel;
import com.back.coach.global.code.DiagnosisSeverity;
import com.back.coach.global.code.ProgressStatus;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RoadmapCommandServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long PROFILE_ID = 10L;
    private static final Long DIAGNOSIS_ID = 20L;
    private static final Long GITHUB_ANALYSIS_ID = 30L;
    private static final Long JOB_ROLE_ID = 100L;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @Mock
    private CapabilityDiagnosisRepository capabilityDiagnosisRepository;

    @Mock
    private GithubAnalysisRepository githubAnalysisRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private JobRoleRepository jobRoleRepository;

    @Mock
    private LearningRoadmapRepository learningRoadmapRepository;

    @Mock
    private RoadmapWeekRepository roadmapWeekRepository;

    @Mock
    private ResultVersionService resultVersionService;

    @Mock
    private LlmClient llmClient;

    @Mock
    private TransactionTemplate transactionTemplate;

    private RoadmapCommandService roadmapCommandService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        given(transactionTemplate.execute(any())).willAnswer(inv -> {
            TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(null);
        });
        roadmapCommandService = new RoadmapCommandService(
                capabilityDiagnosisRepository,
                githubAnalysisRepository,
                userProfileRepository,
                jobRoleRepository,
                learningRoadmapRepository,
                roadmapWeekRepository,
                resultVersionService,
                new RoadmapPromptBuilder(8),
                new RoadmapResponseParser(),
                llmClient,
                objectMapper,
                transactionTemplate,
                org.mockito.Mockito.mock(com.back.coach.domain.context.service.ContextSnapshotPublisher.class)
        );
    }

    @Test
    void createRoadmap_whenInputsAreValid_savesVersionedRoadmapAndWeeks() throws Exception {
        primeValidInputs();
        given(githubAnalysisRepository.existsByIdAndUserId(GITHUB_ANALYSIS_ID, USER_ID)).willReturn(true);
        given(resultVersionService.nextLearningRoadmapVersion(USER_ID)).willReturn(2);
        given(llmClient.complete(anyString(), anyString())).willReturn(validLlmResponse());
        given(learningRoadmapRepository.save(any(LearningRoadmap.class)))
                .willAnswer(invocation -> withIdAndCreatedAt(invocation.getArgument(0), 40L));
        given(roadmapWeekRepository.saveAll(any()))
                .willAnswer(invocation -> withWeekIds(invocation.getArgument(0)));

        RoadmapDetailResponse result = roadmapCommandService.createRoadmap(
                USER_ID,
                new RoadmapRequest(DIAGNOSIS_ID, GITHUB_ANALYSIS_ID, null, 8, LocalDate.of(2099, 12, 31))
        );

        assertThat(result.roadmapId()).isEqualTo("40");
        assertThat(result.version()).isEqualTo(2);
        assertThat(result.totalWeeks()).isEqualTo(2);
        assertThat(result.summary()).isEqualTo("Redis 중심 로드맵");
        assertThat(result.weeks()).hasSize(2);
        assertThat(result.weeks().get(0).roadmapWeekId()).isEqualTo("100");
        assertThat(result.weeks().get(0).progressStatus()).isEqualTo(ProgressStatus.TODO);
        assertThat(result.weeks().get(0).progressNote()).isNull();

        ArgumentCaptor<LearningRoadmap> roadmapCaptor = ArgumentCaptor.forClass(LearningRoadmap.class);
        verify(learningRoadmapRepository).save(roadmapCaptor.capture());
        LearningRoadmap savedRoadmap = roadmapCaptor.getValue();
        assertThat(savedRoadmap.getUserId()).isEqualTo(USER_ID);
        assertThat(savedRoadmap.getDiagnosisId()).isEqualTo(DIAGNOSIS_ID);
        assertThat(savedRoadmap.getVersion()).isEqualTo(2);
        assertThat(savedRoadmap.getTotalWeeks()).isEqualTo(2);
        assertThat(savedRoadmap.getRoadmapPayload()).doesNotContain("progressStatus");

        RoadmapPayload storedPayload = objectMapper.readValue(savedRoadmap.getRoadmapPayload(), RoadmapPayload.class);
        assertThat(storedPayload.weeks()).hasSize(2);
        assertThat(storedPayload.weeks().get(0).topic()).isEqualTo("Redis 기초");

        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmClient).complete(systemCaptor.capture(), userCaptor.capture());
        assertThat(systemCaptor.getValue()).contains("JSON-only");
        assertThat(systemCaptor.getValue()).contains("Korean users");
        assertThat(systemCaptor.getValue()).contains("Write all user-visible natural-language values in Korean");
        assertThat(userCaptor.getValue()).contains("BACKEND_DEVELOPER", "Redis", "weeklyStudyHours: 8");
        assertThat(userCaptor.getValue()).contains("weeks[].topic", "tasks[].title", "materials[].title");
    }

    @Test
    void createRoadmap_whenDiagnosisDoesNotBelongToUser_throwsResourceNotFound() {
        given(capabilityDiagnosisRepository.findByIdAndUserId(DIAGNOSIS_ID, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> roadmapCommandService.createRoadmap(
                USER_ID,
                new RoadmapRequest(DIAGNOSIS_ID, null, null, null, null)
        ))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        verifyNoInteractions(llmClient, learningRoadmapRepository, roadmapWeekRepository);
    }

    @Test
    void createRoadmap_whenGithubAnalysisDoesNotBelongToUser_throwsResourceNotFound() {
        given(capabilityDiagnosisRepository.findByIdAndUserId(DIAGNOSIS_ID, USER_ID))
                .willReturn(Optional.of(diagnosis()));
        given(githubAnalysisRepository.existsByIdAndUserId(GITHUB_ANALYSIS_ID, USER_ID)).willReturn(false);

        assertThatThrownBy(() -> roadmapCommandService.createRoadmap(
                USER_ID,
                new RoadmapRequest(DIAGNOSIS_ID, GITHUB_ANALYSIS_ID, null, null, null)
        ))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        verifyNoInteractions(llmClient, learningRoadmapRepository, roadmapWeekRepository);
    }

    @Test
    void createRoadmap_whenLlmResponseIsInvalid_throwsLlmInvalidResponseAndDoesNotSave() {
        primeValidInputs();
        given(llmClient.complete(anyString(), anyString())).willReturn("{}");

        assertThatThrownBy(() -> roadmapCommandService.createRoadmap(
                USER_ID,
                new RoadmapRequest(DIAGNOSIS_ID, null, null, null, null)
        ))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LLM_INVALID_RESPONSE);

        verify(learningRoadmapRepository, never()).save(any());
        verify(roadmapWeekRepository, never()).saveAll(any());
    }

    private void primeValidInputs() {
        JobRole jobRole = jobRole();
        given(capabilityDiagnosisRepository.findByIdAndUserId(DIAGNOSIS_ID, USER_ID))
                .willReturn(Optional.of(diagnosis()));
        given(userProfileRepository.findByIdAndUserId(PROFILE_ID, USER_ID)).willReturn(Optional.of(profile()));
        given(jobRoleRepository.findById(JOB_ROLE_ID)).willReturn(Optional.of(jobRole));
    }

    private CapabilityDiagnosis diagnosis() {
        CapabilityDiagnosis diagnosis = CapabilityDiagnosis.create(
                USER_ID,
                PROFILE_ID,
                GITHUB_ANALYSIS_ID,
                JOB_ROLE_ID,
                3,
                CurrentLevel.JUNIOR,
                "Redis 보완 필요",
                diagnosisPayloadJson()
        );
        ReflectionTestUtils.setField(diagnosis, "id", DIAGNOSIS_ID);
        return diagnosis;
    }

    private UserProfile profile() {
        UserProfile profile = UserProfile.create(
                USER_ID,
                JOB_ROLE_ID,
                CurrentLevel.JUNIOR,
                10,
                LocalDate.of(2099, 12, 31),
                "[]",
                null,
                null
        );
        ReflectionTestUtils.setField(profile, "id", PROFILE_ID);
        return profile;
    }

    private JobRole jobRole() {
        JobRole jobRole = org.mockito.Mockito.mock(JobRole.class);
        given(jobRole.getRoleCode()).willReturn("BACKEND_DEVELOPER");
        return jobRole;
    }

    private String diagnosisPayloadJson() {
        try {
            return objectMapper.writeValueAsString(new DiagnosisPayload(
                    List.of(new DiagnosisPayload.MissingSkill(
                            "Redis",
                            DiagnosisSeverity.HIGH,
                            "캐시 설계 경험이 부족함",
                            1
                    )),
                    List.of("Spring Boot"),
                    List.of("Redis 캐시와 TTL 기반 설계를 먼저 학습")
            ));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private LearningRoadmap withIdAndCreatedAt(LearningRoadmap roadmap, Long roadmapId) {
        ReflectionTestUtils.setField(roadmap, "id", roadmapId);
        ReflectionTestUtils.setField(roadmap, "createdAt", Instant.parse("2026-04-30T01:00:00Z"));
        return roadmap;
    }

    private List<RoadmapWeek> withWeekIds(List<RoadmapWeek> weeks) {
        List<RoadmapWeek> savedWeeks = new ArrayList<>(weeks);
        for (int i = 0; i < savedWeeks.size(); i++) {
            ReflectionTestUtils.setField(savedWeeks.get(i), "id", 100L + i);
        }
        return savedWeeks;
    }

    private String validLlmResponse() {
        return """
                {
                  "summary": "Redis 중심 로드맵",
                  "weeks": [
                    {
                      "weekNumber": 1,
                      "topic": "Redis 기초",
                      "reason": "캐시 설계 역량을 먼저 보완해야 함",
                      "tasks": [
                        {
                          "type": "READ_DOCS",
                          "title": "Redis 공식 문서 읽기"
                        }
                      ],
                      "materials": [
                        {
                          "type": "DOCS",
                          "title": "Redis Documentation",
                          "url": "https://redis.io/docs"
                        }
                      ],
                      "estimatedHours": 8.0
                    },
                    {
                      "weekNumber": 2,
                      "topic": "Redis 적용",
                      "reason": "실제 프로젝트 적용 경험이 필요함",
                      "tasks": [
                        {
                          "type": "BUILD_EXAMPLE",
                          "title": "캐시 예제 구현"
                        }
                      ],
                      "materials": [],
                      "estimatedHours": 6.0
                    }
                  ]
                }
                """;
    }
}
