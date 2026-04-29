package com.back.coach.domain.diagnosis.service;

import com.back.coach.domain.diagnosis.dto.DiagnosisDetailResponse;
import com.back.coach.domain.diagnosis.entity.CapabilityDiagnosis;
import com.back.coach.domain.diagnosis.repository.CapabilityDiagnosisRepository;
import com.back.coach.domain.jobrole.entity.JobRole;
import com.back.coach.domain.jobrole.repository.JobRoleRepository;
import com.back.coach.global.code.CurrentLevel;
import com.back.coach.global.code.DiagnosisSeverity;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DiagnosisDetailServiceTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @Mock
    private CapabilityDiagnosisRepository capabilityDiagnosisRepository;

    @Mock
    private JobRoleRepository jobRoleRepository;

    private DiagnosisDetailService diagnosisDetailService;

    @BeforeEach
    void setUp() {
        diagnosisDetailService = new DiagnosisDetailService(
                capabilityDiagnosisRepository,
                jobRoleRepository,
                objectMapper
        );
    }

    @Test
    void findDiagnosis_whenDiagnosisExists_returnsParsedDiagnosisDetail() {
        Instant createdAt = Instant.parse("2026-04-29T01:00:00Z");
        CapabilityDiagnosis diagnosis = diagnosis(validPayload(), createdAt);
        JobRole jobRole = jobRole("BACKEND_ENGINEER");
        given(capabilityDiagnosisRepository.findByIdAndUserId(30L, 1L)).willReturn(Optional.of(diagnosis));
        given(jobRoleRepository.findById(100L)).willReturn(Optional.of(jobRole));

        DiagnosisDetailResponse result = diagnosisDetailService.findDiagnosis(1L, 30L);

        assertThat(result.diagnosisId()).isEqualTo("30");
        assertThat(result.version()).isEqualTo(3);
        assertThat(result.profileId()).isEqualTo("10");
        assertThat(result.githubAnalysisId()).isEqualTo("20");
        assertThat(result.targetRole()).isEqualTo("BACKEND_ENGINEER");
        assertThat(result.currentLevel()).isEqualTo(CurrentLevel.JUNIOR);
        assertThat(result.summary()).isEqualTo("Redis 보완 필요");
        assertThat(result.missingSkills()).containsExactly(new DiagnosisDetailResponse.MissingSkillResponse(
                "Redis",
                DiagnosisSeverity.HIGH,
                "캐시 설계 경험이 부족함",
                1
        ));
        assertThat(result.strengths()).containsExactly("Spring Boot", "JPA");
        assertThat(result.recommendations()).containsExactly("Redis 캐시와 TTL 기반 설계를 먼저 학습");
        assertThat(result.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void findDiagnosis_whenDiagnosisDoesNotBelongToUser_throwsResourceNotFound() {
        given(capabilityDiagnosisRepository.findByIdAndUserId(30L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> diagnosisDetailService.findDiagnosis(1L, 30L))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        verifyNoInteractions(jobRoleRepository);
    }

    @Test
    void findDiagnosis_whenPayloadJsonIsInvalid_throwsInternalServerError() {
        CapabilityDiagnosis diagnosis = diagnosisWithPayload("not-json");
        JobRole jobRole = mock(JobRole.class);
        given(capabilityDiagnosisRepository.findByIdAndUserId(30L, 1L)).willReturn(Optional.of(diagnosis));
        given(jobRoleRepository.findById(100L)).willReturn(Optional.of(jobRole));

        assertThatThrownBy(() -> diagnosisDetailService.findDiagnosis(1L, 30L))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Test
    void findDiagnosis_whenJobRoleDoesNotExist_throwsInternalServerError() {
        CapabilityDiagnosis diagnosis = diagnosisWithJobRoleId(100L);
        given(capabilityDiagnosisRepository.findByIdAndUserId(30L, 1L)).willReturn(Optional.of(diagnosis));
        given(jobRoleRepository.findById(100L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> diagnosisDetailService.findDiagnosis(1L, 30L))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private CapabilityDiagnosis diagnosis(String payload, Instant createdAt) {
        CapabilityDiagnosis diagnosis = mock(CapabilityDiagnosis.class);
        given(diagnosis.getId()).willReturn(30L);
        given(diagnosis.getProfileId()).willReturn(10L);
        given(diagnosis.getGithubAnalysisId()).willReturn(20L);
        given(diagnosis.getJobRoleId()).willReturn(100L);
        given(diagnosis.getVersion()).willReturn(3);
        given(diagnosis.getCurrentLevel()).willReturn(CurrentLevel.JUNIOR);
        given(diagnosis.getSummary()).willReturn("Redis 보완 필요");
        given(diagnosis.getDiagnosisPayload()).willReturn(payload);
        given(diagnosis.getCreatedAt()).willReturn(createdAt);
        return diagnosis;
    }

    private CapabilityDiagnosis diagnosisWithPayload(String payload) {
        CapabilityDiagnosis diagnosis = diagnosisWithJobRoleId(100L);
        given(diagnosis.getDiagnosisPayload()).willReturn(payload);
        return diagnosis;
    }

    private CapabilityDiagnosis diagnosisWithJobRoleId(Long jobRoleId) {
        CapabilityDiagnosis diagnosis = mock(CapabilityDiagnosis.class);
        given(diagnosis.getJobRoleId()).willReturn(jobRoleId);
        return diagnosis;
    }

    private JobRole jobRole(String roleCode) {
        JobRole jobRole = mock(JobRole.class);
        given(jobRole.getRoleCode()).willReturn(roleCode);
        return jobRole;
    }

    private String validPayload() {
        return """
                {
                  "missingSkills": [
                    {
                      "skillName": "Redis",
                      "severity": "HIGH",
                      "reason": "캐시 설계 경험이 부족함",
                      "priorityOrder": 1
                    }
                  ],
                  "strengths": ["Spring Boot", "JPA"],
                  "recommendations": ["Redis 캐시와 TTL 기반 설계를 먼저 학습"]
                }
                """;
    }
}
