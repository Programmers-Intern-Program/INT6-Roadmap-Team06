package com.back.coach.domain.diagnosis.service;

import com.back.coach.domain.context.service.ContextSnapshotPublisher;
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
import com.back.coach.external.llm.PromptDirectives;
import com.back.coach.global.code.SkillSourceType;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

@Service
public class DiagnosisCommandService {

    private final UserProfileRepository userProfileRepository;
    private final UserSkillRepository userSkillRepository;
    private final GithubAnalysisRepository githubAnalysisRepository;
    private final JobRoleRepository jobRoleRepository;
    private final SkillRequirementRepository skillRequirementRepository;
    private final CapabilityDiagnosisRepository capabilityDiagnosisRepository;
    private final ResultVersionService resultVersionService;
    private final GithubAnalysisPayloadJson githubAnalysisPayloadJson;
    private final DiagnosisPromptBuilder diagnosisPromptBuilder;
    private final DiagnosisResponseParser diagnosisResponseParser;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final ContextSnapshotPublisher contextSnapshotPublisher;

    public DiagnosisCommandService(
            UserProfileRepository userProfileRepository,
            UserSkillRepository userSkillRepository,
            GithubAnalysisRepository githubAnalysisRepository,
            JobRoleRepository jobRoleRepository,
            SkillRequirementRepository skillRequirementRepository,
            CapabilityDiagnosisRepository capabilityDiagnosisRepository,
            ResultVersionService resultVersionService,
            GithubAnalysisPayloadJson githubAnalysisPayloadJson,
            DiagnosisPromptBuilder diagnosisPromptBuilder,
            DiagnosisResponseParser diagnosisResponseParser,
            LlmClient llmClient,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate,
            ContextSnapshotPublisher contextSnapshotPublisher
    ) {
        this.userProfileRepository = userProfileRepository;
        this.userSkillRepository = userSkillRepository;
        this.githubAnalysisRepository = githubAnalysisRepository;
        this.jobRoleRepository = jobRoleRepository;
        this.skillRequirementRepository = skillRequirementRepository;
        this.capabilityDiagnosisRepository = capabilityDiagnosisRepository;
        this.resultVersionService = resultVersionService;
        this.githubAnalysisPayloadJson = githubAnalysisPayloadJson;
        this.diagnosisPromptBuilder = diagnosisPromptBuilder;
        this.diagnosisResponseParser = diagnosisResponseParser;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.contextSnapshotPublisher = contextSnapshotPublisher;
    }

    public DiagnosisDetailResponse createDiagnosis(Long userId, DiagnosisRequest request) {
        DiagnosisInputs inputs = transactionTemplate.execute(status -> {
            UserProfile profile = userProfileRepository.findByIdAndUserId(request.profileId(), userId)
                    .orElseThrow(() -> new ServiceException(ErrorCode.RESOURCE_NOT_FOUND));
            GithubAnalysis githubAnalysis = githubAnalysisRepository.findByIdAndUserId(request.githubAnalysisId(), userId)
                    .orElseThrow(() -> new ServiceException(ErrorCode.RESOURCE_NOT_FOUND));
            JobRole jobRole = jobRoleRepository.findById(profile.getJobRoleId())
                    .orElseThrow(() -> new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR));
            List<UserSkill> userSkills = userSkillRepository
                    .findByUserIdAndSourceTypeOrderBySkillNameAsc(userId, SkillSourceType.USER_INPUT);
            List<SkillRequirement> skillRequirements = skillRequirementRepository
                    .findByJobRoleIdOrderByImportanceDescSkillNameAsc(profile.getJobRoleId());
            GithubAnalysisPayload githubAnalysisPayload = parseGithubAnalysisPayload(githubAnalysis);
            return new DiagnosisInputs(profile, githubAnalysis, jobRole, userSkills, skillRequirements, githubAnalysisPayload);
        });

        String prompt = diagnosisPromptBuilder.build(new DiagnosisPromptBuilder.Input(
                inputs.jobRole(),
                inputs.profile(),
                inputs.userSkills(),
                inputs.skillRequirements(),
                inputs.githubAnalysisPayload().finalTechProfile()
        ));
        DiagnosisResponseParser.DiagnosisResult diagnosisResult =
                diagnosisResponseParser.parse(
                        llmClient.complete(PromptDirectives.USER_VISIBLE_KOREAN_JSON_ONLY, prompt));
        DiagnosisPayload diagnosisPayload = new DiagnosisPayload(
                diagnosisResult.missingSkills(),
                diagnosisResult.strengths(),
                diagnosisResult.recommendations()
        );

        return transactionTemplate.execute(status -> {
            CapabilityDiagnosis savedDiagnosis = capabilityDiagnosisRepository.save(
                    CapabilityDiagnosis.create(
                            userId,
                            inputs.profile().getId(),
                            inputs.githubAnalysis().getId(),
                            inputs.profile().getJobRoleId(),
                            resultVersionService.nextCapabilityDiagnosisVersion(userId),
                            inputs.profile().getCurrentLevel(),
                            diagnosisResult.summary(),
                            toJson(diagnosisPayload)
                    )
            );
            contextSnapshotPublisher.publishProfile(userId);
            return toResponse(savedDiagnosis, inputs.jobRole(), diagnosisPayload);
        });
    }

    private GithubAnalysisPayload parseGithubAnalysisPayload(GithubAnalysis githubAnalysis) {
        try {
            return githubAnalysisPayloadJson.fromJson(githubAnalysis.getAnalysisPayload());
        } catch (IllegalStateException ex) {
            throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private String toJson(DiagnosisPayload diagnosisPayload) {
        try {
            return objectMapper.writeValueAsString(diagnosisPayload);
        } catch (JsonProcessingException ex) {
            throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private DiagnosisDetailResponse toResponse(
            CapabilityDiagnosis diagnosis,
            JobRole jobRole,
            DiagnosisPayload payload
    ) {
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
                createdAtOrNow(diagnosis)
        );
    }

    private Instant createdAtOrNow(CapabilityDiagnosis diagnosis) {
        return diagnosis.getCreatedAt() == null ? Instant.now() : diagnosis.getCreatedAt();
    }

    private record DiagnosisInputs(
            UserProfile profile,
            GithubAnalysis githubAnalysis,
            JobRole jobRole,
            List<UserSkill> userSkills,
            List<SkillRequirement> skillRequirements,
            GithubAnalysisPayload githubAnalysisPayload
    ) {}
}
