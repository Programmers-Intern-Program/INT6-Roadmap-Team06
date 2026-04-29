package com.back.coach.domain.user.service;

import com.back.coach.domain.jobrole.entity.JobRole;
import com.back.coach.domain.jobrole.repository.JobRoleRepository;
import com.back.coach.domain.user.dto.ProfileDetailResponse;
import com.back.coach.domain.user.dto.ProfileSaveRequest;
import com.back.coach.domain.user.dto.ProfileSaveResult;
import com.back.coach.domain.user.dto.ProfileSkillRequest;
import com.back.coach.domain.user.dto.ProfileSkillResponse;
import com.back.coach.domain.user.entity.UserProfile;
import com.back.coach.domain.user.entity.UserSkill;
import com.back.coach.domain.user.repository.UserProfileRepository;
import com.back.coach.domain.user.repository.UserSkillRepository;
import com.back.coach.global.code.SkillSourceType;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ProfileService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final UserProfileRepository userProfileRepository;
    private final UserSkillRepository userSkillRepository;
    private final JobRoleRepository jobRoleRepository;
    private final ObjectMapper objectMapper;

    public ProfileService(
            UserProfileRepository userProfileRepository,
            UserSkillRepository userSkillRepository,
            JobRoleRepository jobRoleRepository,
            ObjectMapper objectMapper
    ) {
        this.userProfileRepository = userProfileRepository;
        this.userSkillRepository = userSkillRepository;
        this.jobRoleRepository = jobRoleRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ProfileSaveResult saveProfile(Long userId, ProfileSaveRequest request) {
        JobRole jobRole = findActiveJobRole(request.targetRole());
        List<ProfileSkillRequest> normalizedSkills = normalizeSkills(request.skills());
        String interestAreasJson = toJson(normalizeInterestAreas(request.interestAreas()));

        UserProfile profile = userProfileRepository.findByUserId(userId)
                .map(existingProfile -> {
                    existingProfile.update(
                            jobRole.getId(),
                            request.currentLevel(),
                            request.weeklyStudyHours(),
                            request.targetDate(),
                            interestAreasJson,
                            request.resumeAssetId(),
                            request.portfolioAssetId()
                    );
                    return existingProfile;
                })
                .orElseGet(() -> UserProfile.create(
                        userId,
                        jobRole.getId(),
                        request.currentLevel(),
                        request.weeklyStudyHours(),
                        request.targetDate(),
                        interestAreasJson,
                        request.resumeAssetId(),
                        request.portfolioAssetId()
                ));

        UserProfile savedProfile = userProfileRepository.saveAndFlush(profile);
        replaceUserInputSkills(userId, normalizedSkills);

        return new ProfileSaveResult(savedProfile.getId(), savedProfile.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public ProfileDetailResponse findMyProfile(Long userId) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ServiceException(ErrorCode.RESOURCE_NOT_FOUND));
        JobRole jobRole = jobRoleRepository.findById(profile.getJobRoleId())
                .orElseThrow(() -> new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR));
        List<ProfileSkillResponse> skills = userSkillRepository
                .findByUserIdAndSourceTypeOrderBySkillNameAsc(userId, SkillSourceType.USER_INPUT)
                .stream()
                .map(skill -> new ProfileSkillResponse(
                        skill.getSkillName(),
                        skill.getProficiencyLevel(),
                        skill.getSourceType()
                ))
                .toList();

        return new ProfileDetailResponse(
                String.valueOf(profile.getId()),
                jobRole.getRoleCode(),
                profile.getCurrentLevel(),
                skills,
                parseInterestAreas(profile.getInterestAreasJson()),
                profile.getWeeklyStudyHours(),
                profile.getTargetDate(),
                toNullableString(profile.getResumeAssetId()),
                toNullableString(profile.getPortfolioAssetId())
        );
    }

    private JobRole findActiveJobRole(String targetRole) {
        return jobRoleRepository.findByRoleCodeAndActiveTrue(targetRole.trim())
                .orElseThrow(() -> new ServiceException(ErrorCode.INVALID_INPUT));
    }

    private List<ProfileSkillRequest> normalizeSkills(List<ProfileSkillRequest> skills) {
        Set<String> normalizedSkillNames = new HashSet<>();
        return skills.stream()
                .map(skill -> {
                    String skillName = skill.skillName().trim();
                    String normalizedName = skillName.toLowerCase(Locale.ROOT);
                    if (!normalizedSkillNames.add(normalizedName)) {
                        throw new ServiceException(ErrorCode.INVALID_INPUT);
                    }
                    return new ProfileSkillRequest(skillName, skill.proficiencyLevel());
                })
                .toList();
    }

    private List<String> normalizeInterestAreas(List<String> interestAreas) {
        if (interestAreas == null) {
            return List.of();
        }
        return interestAreas.stream()
                .map(String::trim)
                .toList();
    }

    private void replaceUserInputSkills(Long userId, List<ProfileSkillRequest> skills) {
        List<UserSkill> existingUserInputSkills = userSkillRepository
                .findByUserIdAndSourceTypeOrderBySkillNameAsc(userId, SkillSourceType.USER_INPUT);
        userSkillRepository.deleteAll(existingUserInputSkills);
        userSkillRepository.flush();
        userSkillRepository.saveAll(skills.stream()
                .map(skill -> UserSkill.create(
                        userId,
                        skill.skillName(),
                        skill.proficiencyLevel(),
                        SkillSourceType.USER_INPUT
                ))
                .toList());
    }

    private String toJson(List<String> interestAreas) {
        try {
            return objectMapper.writeValueAsString(interestAreas);
        } catch (JsonProcessingException ex) {
            throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private List<String> parseInterestAreas(String interestAreasJson) {
        try {
            return objectMapper.readValue(interestAreasJson, STRING_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private String toNullableString(Long value) {
        return value == null ? null : String.valueOf(value);
    }
}
