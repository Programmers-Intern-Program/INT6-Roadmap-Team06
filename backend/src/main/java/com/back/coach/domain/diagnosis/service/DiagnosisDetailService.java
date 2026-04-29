package com.back.coach.domain.diagnosis.service;

import com.back.coach.domain.diagnosis.dto.DiagnosisDetailResponse;
import com.back.coach.domain.diagnosis.dto.DiagnosisPayload;
import com.back.coach.domain.diagnosis.entity.CapabilityDiagnosis;
import com.back.coach.domain.diagnosis.repository.CapabilityDiagnosisRepository;
import com.back.coach.domain.jobrole.entity.JobRole;
import com.back.coach.domain.jobrole.repository.JobRoleRepository;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiagnosisDetailService {

    private final CapabilityDiagnosisRepository capabilityDiagnosisRepository;
    private final JobRoleRepository jobRoleRepository;
    private final ObjectMapper objectMapper;

    public DiagnosisDetailService(
            CapabilityDiagnosisRepository capabilityDiagnosisRepository,
            JobRoleRepository jobRoleRepository,
            ObjectMapper objectMapper
    ) {
        this.capabilityDiagnosisRepository = capabilityDiagnosisRepository;
        this.jobRoleRepository = jobRoleRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public DiagnosisDetailResponse findDiagnosis(Long userId, Long diagnosisId) {
        CapabilityDiagnosis diagnosis = capabilityDiagnosisRepository.findByIdAndUserId(diagnosisId, userId)
                .orElseThrow(() -> new ServiceException(ErrorCode.RESOURCE_NOT_FOUND));
        JobRole jobRole = jobRoleRepository.findById(diagnosis.getJobRoleId())
                .orElseThrow(() -> new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR));
        DiagnosisPayload payload = parsePayload(diagnosis.getDiagnosisPayload());

        return new DiagnosisDetailResponse(
                String.valueOf(diagnosis.getId()),
                diagnosis.getVersion(),
                String.valueOf(diagnosis.getProfileId()),
                String.valueOf(diagnosis.getGithubAnalysisId()),
                jobRole.getRoleCode(),
                diagnosis.getCurrentLevel(),
                diagnosis.getSummary(),
                payload.missingSkills().stream()
                        .map(DiagnosisDetailResponse.MissingSkillResponse::from)
                        .toList(),
                payload.strengths(),
                payload.recommendations(),
                diagnosis.getCreatedAt()
        );
    }

    private DiagnosisPayload parsePayload(String diagnosisPayload) {
        try {
            return objectMapper.readValue(diagnosisPayload, DiagnosisPayload.class);
        } catch (JsonProcessingException ex) {
            throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
