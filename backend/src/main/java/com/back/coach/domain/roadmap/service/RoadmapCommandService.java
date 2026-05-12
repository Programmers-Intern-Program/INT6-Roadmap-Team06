package com.back.coach.domain.roadmap.service;

import com.back.coach.domain.context.service.ContextSnapshotPublisher;
import com.back.coach.domain.diagnosis.dto.DiagnosisPayload;
import com.back.coach.domain.diagnosis.entity.CapabilityDiagnosis;
import com.back.coach.domain.diagnosis.repository.CapabilityDiagnosisRepository;
import com.back.coach.domain.github.repository.GithubAnalysisRepository;
import com.back.coach.domain.jobrole.entity.JobRole;
import com.back.coach.domain.jobrole.repository.JobRoleRepository;
import com.back.coach.domain.result.service.ResultVersionService;
import com.back.coach.domain.roadmap.dto.RoadmapDetailResponse;
import com.back.coach.domain.roadmap.dto.RoadmapDetailSnapshot;
import com.back.coach.domain.roadmap.dto.RoadmapPayload;
import com.back.coach.domain.roadmap.dto.RoadmapRequest;
import com.back.coach.domain.roadmap.entity.LearningRoadmap;
import com.back.coach.domain.roadmap.entity.RoadmapWeek;
import com.back.coach.domain.roadmap.repository.LearningRoadmapRepository;
import com.back.coach.domain.roadmap.repository.RoadmapWeekRepository;
import com.back.coach.domain.user.entity.UserProfile;
import com.back.coach.domain.user.repository.UserProfileRepository;
import com.back.coach.external.llm.LlmClient;
import com.back.coach.external.llm.PromptDirectives;
import com.back.coach.global.code.ProgressStatus;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

@Service
public class RoadmapCommandService {

    private final CapabilityDiagnosisRepository capabilityDiagnosisRepository;
    private final GithubAnalysisRepository githubAnalysisRepository;
    private final UserProfileRepository userProfileRepository;
    private final JobRoleRepository jobRoleRepository;
    private final LearningRoadmapRepository learningRoadmapRepository;
    private final RoadmapWeekRepository roadmapWeekRepository;
    private final ResultVersionService resultVersionService;
    private final RoadmapPromptBuilder roadmapPromptBuilder;
    private final RoadmapResponseParser roadmapResponseParser;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final ContextSnapshotPublisher contextSnapshotPublisher;

    public RoadmapCommandService(
            CapabilityDiagnosisRepository capabilityDiagnosisRepository,
            GithubAnalysisRepository githubAnalysisRepository,
            UserProfileRepository userProfileRepository,
            JobRoleRepository jobRoleRepository,
            LearningRoadmapRepository learningRoadmapRepository,
            RoadmapWeekRepository roadmapWeekRepository,
            ResultVersionService resultVersionService,
            RoadmapPromptBuilder roadmapPromptBuilder,
            RoadmapResponseParser roadmapResponseParser,
            LlmClient llmClient,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate,
            ContextSnapshotPublisher contextSnapshotPublisher
    ) {
        this.capabilityDiagnosisRepository = capabilityDiagnosisRepository;
        this.githubAnalysisRepository = githubAnalysisRepository;
        this.userProfileRepository = userProfileRepository;
        this.jobRoleRepository = jobRoleRepository;
        this.learningRoadmapRepository = learningRoadmapRepository;
        this.roadmapWeekRepository = roadmapWeekRepository;
        this.resultVersionService = resultVersionService;
        this.roadmapPromptBuilder = roadmapPromptBuilder;
        this.roadmapResponseParser = roadmapResponseParser;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.contextSnapshotPublisher = contextSnapshotPublisher;
    }

    // LLM 호출을 트랜잭션 밖에서 수행해 DB 커넥션을 장시간 점유하지 않도록 분리
    public RoadmapDetailResponse createRoadmap(Long userId, RoadmapRequest request) {
        // 1. DB 조회 (짧은 트랜잭션)
        record Inputs(CapabilityDiagnosis diagnosis, UserProfile profile, JobRole jobRole, DiagnosisPayload payload) {}
        Inputs inputs = transactionTemplate.execute(status -> {
            CapabilityDiagnosis diagnosis = capabilityDiagnosisRepository.findByIdAndUserId(request.diagnosisId(), userId)
                    .orElseThrow(() -> new ServiceException(ErrorCode.RESOURCE_NOT_FOUND));
            validateGithubAnalysisOwnership(userId, request.githubAnalysisId());
            UserProfile profile = userProfileRepository.findByIdAndUserId(diagnosis.getProfileId(), userId)
                    .orElseThrow(() -> new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR));
            JobRole jobRole = jobRoleRepository.findById(diagnosis.getJobRoleId())
                    .orElseThrow(() -> new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR));
            return new Inputs(diagnosis, profile, jobRole, parseDiagnosisPayload(diagnosis));
        });

        // 2. LLM 호출 (트랜잭션 밖)
        String prompt = roadmapPromptBuilder.build(new RoadmapPromptBuilder.Input(
                inputs.jobRole(), inputs.profile(), inputs.diagnosis(), inputs.payload(),
                request.weeklyStudyHours(), request.targetDate()
        ));
        RoadmapResponseParser.RoadmapResult roadmapResult =
                roadmapResponseParser.parse(
                        llmClient.complete(PromptDirectives.NO_REASONING_JSON_ONLY, prompt));

        // 3. DB 저장 (새 트랜잭션)
        RoadmapDetailResponse response = transactionTemplate.execute(status -> {
            RoadmapPayload roadmapPayload = new RoadmapPayload(roadmapResult.weeks());
            LearningRoadmap savedRoadmap = learningRoadmapRepository.save(LearningRoadmap.create(
                    userId,
                    inputs.diagnosis().getId(),
                    resultVersionService.nextLearningRoadmapVersion(userId),
                    roadmapResult.weeks().size(),
                    roadmapResult.summary(),
                    toJson(roadmapPayload)
            ));
            List<RoadmapWeek> savedWeeks = roadmapWeekRepository.saveAll(toRoadmapWeeks(
                    savedRoadmap.getId(), roadmapResult.weeks()
            ));
            contextSnapshotPublisher.publishPlan(userId);
            return RoadmapDetailResponse.from(toSnapshot(savedRoadmap, savedWeeks), objectMapper);
        });
        return response;
    }

    private void validateGithubAnalysisOwnership(Long userId, Long githubAnalysisId) {
        if (githubAnalysisId != null && !githubAnalysisRepository.existsByIdAndUserId(githubAnalysisId, userId)) {
            throw new ServiceException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private DiagnosisPayload parseDiagnosisPayload(CapabilityDiagnosis diagnosis) {
        try {
            return objectMapper.readValue(diagnosis.getDiagnosisPayload(), DiagnosisPayload.class);
        } catch (JsonProcessingException ex) {
            throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private List<RoadmapWeek> toRoadmapWeeks(Long roadmapId, List<RoadmapPayload.Week> weeks) {
        return weeks.stream()
                .map(week -> RoadmapWeek.create(
                        roadmapId,
                        week.weekNumber(),
                        week.topic(),
                        week.reason(),
                        toJson(week.tasks()),
                        toJson(week.materials()),
                        week.estimatedHours()
                ))
                .toList();
    }

    private RoadmapDetailSnapshot toSnapshot(LearningRoadmap roadmap, List<RoadmapWeek> weeks) {
        return new RoadmapDetailSnapshot(
                roadmap.getId(),
                roadmap.getVersion(),
                roadmap.getTotalWeeks(),
                roadmap.getSummary(),
                createdAtOrNow(roadmap),
                weeks.stream()
                        .map(this::toWeekSnapshot)
                        .toList()
        );
    }

    private RoadmapDetailSnapshot.WeekSnapshot toWeekSnapshot(RoadmapWeek week) {
        return new RoadmapDetailSnapshot.WeekSnapshot(
                week.getId(),
                week.getWeekNumber(),
                week.getTopic(),
                week.getReasonText(),
                week.getTasksJson(),
                week.getMaterialsJson(),
                week.getEstimatedHours(),
                ProgressStatus.TODO,
                null,
                null
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private Instant createdAtOrNow(LearningRoadmap roadmap) {
        return roadmap.getCreatedAt() == null ? Instant.now() : roadmap.getCreatedAt();
    }
}
