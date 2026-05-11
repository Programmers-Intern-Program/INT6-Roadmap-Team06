package com.back.coach.domain.context.service;

import com.back.coach.domain.diagnosis.entity.CapabilityDiagnosis;
import com.back.coach.domain.diagnosis.repository.CapabilityDiagnosisRepository;
import com.back.coach.domain.github.entity.GithubAnalysis;
import com.back.coach.domain.github.repository.GithubAnalysisRepository;
import com.back.coach.domain.jobrole.entity.JobRole;
import com.back.coach.domain.jobrole.repository.JobRoleRepository;
import com.back.coach.domain.roadmap.entity.LearningRoadmap;
import com.back.coach.domain.roadmap.repository.LearningRoadmapRepository;
import com.back.coach.domain.user.entity.UserProfile;
import com.back.coach.domain.user.entity.UserSkill;
import com.back.coach.domain.user.repository.UserProfileRepository;
import com.back.coach.domain.user.repository.UserSkillRepository;
import com.back.coach.global.code.ContextType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * v1 결과 변경 이후 PROFILE/PLAN snapshot을 생성한다.
 *
 * 호출 시점에 활성 트랜잭션이 있으면 afterCommit 훅으로 지연시켜 v1 저장 흐름을 보호한다.
 * snapshot 생성 실패는 항상 swallow + log.warn.
 */
@Service
public class ContextSnapshotPublisher {

    private static final Logger log = LoggerFactory.getLogger(ContextSnapshotPublisher.class);

    private final ContextSnapshotStorageService storageService;
    private final UserProfileRepository userProfileRepository;
    private final UserSkillRepository userSkillRepository;
    private final JobRoleRepository jobRoleRepository;
    private final GithubAnalysisRepository githubAnalysisRepository;
    private final CapabilityDiagnosisRepository capabilityDiagnosisRepository;
    private final LearningRoadmapRepository learningRoadmapRepository;
    private final ObjectMapper objectMapper;

    public ContextSnapshotPublisher(
            ContextSnapshotStorageService storageService,
            UserProfileRepository userProfileRepository,
            UserSkillRepository userSkillRepository,
            JobRoleRepository jobRoleRepository,
            GithubAnalysisRepository githubAnalysisRepository,
            CapabilityDiagnosisRepository capabilityDiagnosisRepository,
            LearningRoadmapRepository learningRoadmapRepository,
            ObjectMapper objectMapper
    ) {
        this.storageService = storageService;
        this.userProfileRepository = userProfileRepository;
        this.userSkillRepository = userSkillRepository;
        this.jobRoleRepository = jobRoleRepository;
        this.githubAnalysisRepository = githubAnalysisRepository;
        this.capabilityDiagnosisRepository = capabilityDiagnosisRepository;
        this.learningRoadmapRepository = learningRoadmapRepository;
        this.objectMapper = objectMapper;
    }

    public void publishProfile(Long userId) {
        runAfterCommit(() -> doPublishProfile(userId));
    }

    public void publishPlan(Long userId) {
        runAfterCommit(() -> doPublishPlan(userId));
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private void doPublishProfile(Long userId) {
        try {
            String payload = buildProfilePayload(userId);
            storageService.createSnapshot(userId, ContextType.PROFILE, payload);
        } catch (Exception e) {
            log.warn("PROFILE snapshot publish failed userId={} reason={}", userId, e.getMessage());
        }
    }

    private void doPublishPlan(Long userId) {
        try {
            String payload = buildPlanPayload(userId);
            storageService.createSnapshot(userId, ContextType.PLAN, payload);
        } catch (Exception e) {
            log.warn("PLAN snapshot publish failed userId={} reason={}", userId, e.getMessage());
        }
    }

    private String buildProfilePayload(Long userId) throws JsonProcessingException {
        Optional<UserProfile> profile = userProfileRepository.findByUserId(userId);
        Optional<GithubAnalysis> github = githubAnalysisRepository.findTopByUserIdOrderByVersionDescCreatedAtDesc(userId);
        Optional<CapabilityDiagnosis> diagnosis = capabilityDiagnosisRepository.findTopByUserIdOrderByVersionDescCreatedAtDesc(userId);

        ObjectNode root = objectMapper.createObjectNode();
        root.put("contextType", "PROFILE");
        root.put("generatedAt", Instant.now().toString());

        ObjectNode sourceRefs = root.putObject("sourceRefs");
        profile.ifPresent(p -> sourceRefs.put("profileId", String.valueOf(p.getId())));
        github.ifPresent(g -> {
            sourceRefs.put("githubAnalysisId", String.valueOf(g.getId()));
            sourceRefs.put("githubAnalysisVersion", g.getVersion());
        });
        diagnosis.ifPresent(d -> {
            sourceRefs.put("diagnosisId", String.valueOf(d.getId()));
            sourceRefs.put("diagnosisVersion", d.getVersion());
        });

        ObjectNode profileNode = root.putObject("profile");
        if (profile.isPresent()) {
            UserProfile p = profile.get();
            JobRole jobRole = jobRoleRepository.findById(p.getJobRoleId()).orElse(null);
            if (jobRole != null) {
                profileNode.put("targetRole", jobRole.getRoleCode());
            }
            profileNode.put("currentLevel", p.getCurrentLevel().name());
            if (p.getWeeklyStudyHours() != null) {
                profileNode.put("weeklyStudyHours", p.getWeeklyStudyHours());
            }
            if (p.getInterestAreasJson() != null && !p.getInterestAreasJson().isBlank()) {
                profileNode.set("interestAreas", objectMapper.readTree(p.getInterestAreasJson()));
            }
        }

        ArrayNode skillsArr = root.putArray("skills");
        List<UserSkill> skills = userSkillRepository.findByUserIdOrderBySkillNameAsc(userId);
        for (UserSkill s : skills) {
            ObjectNode sn = skillsArr.addObject();
            sn.put("skillName", s.getSkillName());
            sn.put("sourceType", s.getSourceType().name());
            if (s.getProficiencyLevel() != null) {
                sn.put("proficiencyLevel", s.getProficiencyLevel().name());
            }
        }

        if (github.isPresent() && github.get().getAnalysisPayload() != null) {
            try {
                ObjectNode techProfile = (ObjectNode) objectMapper.readTree(github.get().getAnalysisPayload())
                        .path("finalTechProfile");
                if (!techProfile.isMissingNode()) {
                    root.set("githubFinalTechProfile", techProfile);
                }
            } catch (Exception ignored) {
                // tolerate parse issues; leave field absent
            }
        }

        if (diagnosis.isPresent()) {
            ObjectNode dx = root.putObject("diagnosisSummary");
            dx.put("summary", diagnosis.get().getSummary());
        }

        return objectMapper.writeValueAsString(root);
    }

    private String buildPlanPayload(Long userId) throws JsonProcessingException {
        Optional<LearningRoadmap> roadmap = learningRoadmapRepository.findTopByUserIdOrderByVersionDescCreatedAtDesc(userId);
        Optional<CapabilityDiagnosis> diagnosis = capabilityDiagnosisRepository.findTopByUserIdOrderByVersionDescCreatedAtDesc(userId);

        ObjectNode root = objectMapper.createObjectNode();
        root.put("contextType", "PLAN");
        Instant now = Instant.now();
        root.put("generatedAt", now.toString());

        ObjectNode sourceRefs = root.putObject("sourceRefs");
        roadmap.ifPresent(r -> {
            sourceRefs.put("roadmapId", String.valueOf(r.getId()));
            sourceRefs.put("roadmapVersion", r.getVersion());
        });
        diagnosis.ifPresent(d -> {
            sourceRefs.put("diagnosisId", String.valueOf(d.getId()));
            sourceRefs.put("diagnosisVersion", d.getVersion());
        });

        root.put("progressAsOf", now.toString());

        ObjectNode rmNode = root.putObject("roadmap");
        if (roadmap.isPresent()) {
            LearningRoadmap r = roadmap.get();
            rmNode.put("totalWeeks", r.getTotalWeeks());
            rmNode.put("summary", r.getSummary());
        }

        return objectMapper.writeValueAsString(root);
    }
}
