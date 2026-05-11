package com.back.coach.domain.diagnosis.service;

import com.back.coach.domain.diagnosis.dto.DiagnosisDetailResponse;
import com.back.coach.domain.diagnosis.dto.DiagnosisPayload;
import com.back.coach.domain.diagnosis.dto.DiagnosisRequest;
import com.back.coach.domain.diagnosis.entity.CapabilityDiagnosis;
import com.back.coach.domain.diagnosis.repository.CapabilityDiagnosisRepository;
import com.back.coach.domain.github.dto.GithubAnalysisPayload;
import com.back.coach.domain.github.entity.GithubAnalysis;
import com.back.coach.domain.github.repository.GithubAnalysisRepository;
import com.back.coach.domain.github.service.GithubAnalysisPayloadJson;
import com.back.coach.domain.jobrole.entity.JobRole;
import com.back.coach.domain.jobrole.entity.SkillRequirement;
import com.back.coach.domain.jobrole.repository.JobRoleRepository;
import com.back.coach.domain.jobrole.repository.SkillRequirementRepository;
import com.back.coach.domain.result.service.ResultVersionService;
import com.back.coach.domain.user.entity.UserProfile;
import com.back.coach.domain.user.entity.UserSkill;
import com.back.coach.domain.user.repository.UserProfileRepository;
import com.back.coach.domain.user.repository.UserSkillRepository;
import com.back.coach.external.llm.LlmClient;
import com.back.coach.global.code.CurrentLevel;
import com.back.coach.global.code.DiagnosisSeverity;
import com.back.coach.global.code.ProficiencyLevel;
import com.back.coach.global.code.SkillSourceType;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
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
class DiagnosisCommandServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long PROFILE_ID = 10L;
    private static final Long GITHUB_ANALYSIS_ID = 20L;
    private static final Long JOB_ROLE_ID = 100L;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();
    private final GithubAnalysisPayloadJson githubAnalysisPayloadJson = new GithubAnalysisPayloadJson();

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private UserSkillRepository userSkillRepository;

    @Mock
    private GithubAnalysisRepository githubAnalysisRepository;

    @Mock
    private JobRoleRepository jobRoleRepository;

    @Mock
    private SkillRequirementRepository skillRequirementRepository;

    @Mock
    private CapabilityDiagnosisRepository capabilityDiagnosisRepository;

    @Mock
    private ResultVersionService resultVersionService;

    @Mock
    private LlmClient llmClient;

    private DiagnosisCommandService diagnosisCommandService;

    @BeforeEach
    void setUp() {
        diagnosisCommandService = new DiagnosisCommandService(
                userProfileRepository,
                userSkillRepository,
                githubAnalysisRepository,
                jobRoleRepository,
                skillRequirementRepository,
                capabilityDiagnosisRepository,
                resultVersionService,
                githubAnalysisPayloadJson,
                new DiagnosisPromptBuilder(),
                new DiagnosisResponseParser(),
                llmClient,
                objectMapper,
                transactionTemplate(),
                org.mockito.Mockito.mock(com.back.coach.domain.context.service.ContextSnapshotPublisher.class)
        );
    }

    @Test
    void createDiagnosis_whenInputsAreValid_savesVersionedDiagnosisPayload() throws Exception {
        primeValidInputs();
        given(resultVersionService.nextCapabilityDiagnosisVersion(USER_ID)).willReturn(4);
        given(llmClient.complete(anyString())).willReturn(validLlmResponse());
        given(capabilityDiagnosisRepository.save(any(CapabilityDiagnosis.class)))
                .willAnswer(invocation -> withIdAndCreatedAt(invocation.getArgument(0), 30L));

        DiagnosisDetailResponse result = diagnosisCommandService.createDiagnosis(
                USER_ID,
                new DiagnosisRequest(PROFILE_ID, GITHUB_ANALYSIS_ID)
        );

        assertThat(result.diagnosisId()).isEqualTo("30");
        assertThat(result.version()).isEqualTo(4);
        assertThat(result.profileId()).isEqualTo("10");
        assertThat(result.githubAnalysisId()).isEqualTo("20");
        assertThat(result.targetRole()).isEqualTo("BACKEND_DEVELOPER");
        assertThat(result.summary()).isEqualTo("Redis 보완 필요");
        assertThat(result.missingSkills()).containsExactly(new DiagnosisDetailResponse.MissingSkillResponse(
                "Redis",
                DiagnosisSeverity.HIGH,
                "캐시 설계 경험이 부족함",
                1
        ));

        ArgumentCaptor<CapabilityDiagnosis> diagnosisCaptor = ArgumentCaptor.forClass(CapabilityDiagnosis.class);
        verify(capabilityDiagnosisRepository).save(diagnosisCaptor.capture());
        CapabilityDiagnosis saved = diagnosisCaptor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getProfileId()).isEqualTo(PROFILE_ID);
        assertThat(saved.getGithubAnalysisId()).isEqualTo(GITHUB_ANALYSIS_ID);
        assertThat(saved.getJobRoleId()).isEqualTo(JOB_ROLE_ID);
        assertThat(saved.getVersion()).isEqualTo(4);
        assertThat(saved.getCurrentLevel()).isEqualTo(CurrentLevel.JUNIOR);

        DiagnosisPayload storedPayload = objectMapper.readValue(saved.getDiagnosisPayload(), DiagnosisPayload.class);
        assertThat(storedPayload.missingSkills())
                .extracting(DiagnosisPayload.MissingSkill::skillName)
                .containsExactly("Redis");
        assertThat(storedPayload.strengths()).containsExactly("Spring Boot");
        assertThat(storedPayload.recommendations()).containsExactly("Redis 캐시와 TTL 기반 설계를 먼저 학습");

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmClient).complete(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains("Spring Boot", "Redis", "BACKEND_DEVELOPER");
    }

    @Test
    void createDiagnosis_whenProfileDoesNotBelongToUser_throwsResourceNotFound() {
        given(userProfileRepository.findByIdAndUserId(PROFILE_ID, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> diagnosisCommandService.createDiagnosis(
                USER_ID,
                new DiagnosisRequest(PROFILE_ID, GITHUB_ANALYSIS_ID)
        ))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        verifyNoInteractions(githubAnalysisRepository, llmClient, capabilityDiagnosisRepository);
    }

    @Test
    void createDiagnosis_whenGithubAnalysisDoesNotBelongToUser_throwsResourceNotFound() {
        UserProfile profile = profile();
        given(userProfileRepository.findByIdAndUserId(PROFILE_ID, USER_ID)).willReturn(Optional.of(profile));
        given(githubAnalysisRepository.findByIdAndUserId(GITHUB_ANALYSIS_ID, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> diagnosisCommandService.createDiagnosis(
                USER_ID,
                new DiagnosisRequest(PROFILE_ID, GITHUB_ANALYSIS_ID)
        ))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        verifyNoInteractions(llmClient, capabilityDiagnosisRepository);
    }

    @Test
    void createDiagnosis_whenGithubAnalysisPayloadIsInvalid_throwsInternalServerError() {
        UserProfile profile = profile();
        GithubAnalysis analysis = GithubAnalysis.create(USER_ID, 200L, 1, "summary", "not-json");
        JobRole jobRole = org.mockito.Mockito.mock(JobRole.class);
        given(userProfileRepository.findByIdAndUserId(PROFILE_ID, USER_ID)).willReturn(Optional.of(profile));
        given(githubAnalysisRepository.findByIdAndUserId(GITHUB_ANALYSIS_ID, USER_ID)).willReturn(Optional.of(analysis));
        given(jobRoleRepository.findById(JOB_ROLE_ID)).willReturn(Optional.of(jobRole));
        given(userSkillRepository.findByUserIdAndSourceTypeOrderBySkillNameAsc(USER_ID, SkillSourceType.USER_INPUT))
                .willReturn(List.of());
        given(skillRequirementRepository.findByJobRoleIdOrderByImportanceDescSkillNameAsc(JOB_ROLE_ID))
                .willReturn(List.of());

        assertThatThrownBy(() -> diagnosisCommandService.createDiagnosis(
                USER_ID,
                new DiagnosisRequest(PROFILE_ID, GITHUB_ANALYSIS_ID)
        ))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);

        verifyNoInteractions(llmClient, capabilityDiagnosisRepository);
    }

    @Test
    void createDiagnosis_whenLlmResponseIsInvalid_throwsLlmInvalidResponseAndDoesNotSave() {
        primeValidInputs();
        given(llmClient.complete(anyString())).willReturn("{}");

        assertThatThrownBy(() -> diagnosisCommandService.createDiagnosis(
                USER_ID,
                new DiagnosisRequest(PROFILE_ID, GITHUB_ANALYSIS_ID)
        ))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LLM_INVALID_RESPONSE);

        verify(capabilityDiagnosisRepository, never()).save(any());
    }

    private void primeValidInputs() {
        UserProfile profile = profile();
        GithubAnalysis analysis = GithubAnalysis.create(USER_ID, 200L, 1, "summary",
                githubAnalysisPayloadJson.toJson(githubPayload()));
        JobRole jobRole = jobRole();
        SkillRequirement springBoot = skillRequirement("Spring Boot", 5);
        SkillRequirement redis = skillRequirement("Redis", 3);
        ReflectionTestUtils.setField(analysis, "id", GITHUB_ANALYSIS_ID);
        given(userProfileRepository.findByIdAndUserId(PROFILE_ID, USER_ID)).willReturn(Optional.of(profile));
        given(githubAnalysisRepository.findByIdAndUserId(GITHUB_ANALYSIS_ID, USER_ID)).willReturn(Optional.of(analysis));
        given(jobRoleRepository.findById(JOB_ROLE_ID)).willReturn(Optional.of(jobRole));
        given(userSkillRepository.findByUserIdAndSourceTypeOrderBySkillNameAsc(USER_ID, SkillSourceType.USER_INPUT))
                .willReturn(List.of(UserSkill.create(USER_ID, "Spring Boot", ProficiencyLevel.WORKING, SkillSourceType.USER_INPUT)));
        given(skillRequirementRepository.findByJobRoleIdOrderByImportanceDescSkillNameAsc(JOB_ROLE_ID))
                .willReturn(List.of(springBoot, redis));
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
        given(jobRole.getRoleName()).willReturn("백엔드 개발자");
        given(jobRole.getDescription()).willReturn("Java와 Spring Boot 기반 백엔드 직무");
        return jobRole;
    }

    private SkillRequirement skillRequirement(String skillName, int importance) {
        SkillRequirement requirement = org.mockito.Mockito.mock(SkillRequirement.class);
        given(requirement.getSkillName()).willReturn(skillName);
        given(requirement.getCategory()).willReturn("BACKEND");
        given(requirement.getImportance()).willReturn(importance);
        return requirement;
    }

    private GithubAnalysisPayload githubPayload() {
        return new GithubAnalysisPayload(
                new GithubAnalysisPayload.StaticSignals(List.of(), 1, "WEEKLY", "CONSISTENT"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new GithubAnalysisPayload.FinalTechProfile(List.of("Spring Boot"), List.of("Backend"))
        );
    }

    private CapabilityDiagnosis withIdAndCreatedAt(CapabilityDiagnosis diagnosis, Long diagnosisId) {
        ReflectionTestUtils.setField(diagnosis, "id", diagnosisId);
        ReflectionTestUtils.setField(diagnosis, "createdAt", Instant.parse("2026-04-29T01:00:00Z"));
        return diagnosis;
    }

    private String validLlmResponse() {
        return """
                {
                  "summary": "Redis 보완 필요",
                  "missingSkills": [
                    {
                      "skillName": "Redis",
                      "severity": "HIGH",
                      "reason": "캐시 설계 경험이 부족함",
                      "priorityOrder": 1
                    }
                  ],
                  "strengths": ["Spring Boot"],
                  "recommendations": ["Redis 캐시와 TTL 기반 설계를 먼저 학습"]
                }
                """;
    }

    private static TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        });
    }
}
