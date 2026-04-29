package com.back.coach.domain.user.service;

import com.back.coach.domain.jobrole.entity.JobRole;
import com.back.coach.domain.jobrole.repository.JobRoleRepository;
import com.back.coach.domain.user.dto.ProfileDetailResponse;
import com.back.coach.domain.user.dto.ProfileSaveRequest;
import com.back.coach.domain.user.dto.ProfileSaveResult;
import com.back.coach.domain.user.dto.ProfileSkillRequest;
import com.back.coach.domain.user.entity.UserProfile;
import com.back.coach.domain.user.entity.UserSkill;
import com.back.coach.domain.user.repository.UserProfileRepository;
import com.back.coach.domain.user.repository.UserSkillRepository;
import com.back.coach.global.code.CurrentLevel;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private UserSkillRepository userSkillRepository;

    @Mock
    private JobRoleRepository jobRoleRepository;

    private ProfileService profileService;

    @BeforeEach
    void setUp() {
        profileService = new ProfileService(
                userProfileRepository,
                userSkillRepository,
                jobRoleRepository,
                objectMapper
        );
    }

    @Test
    void saveProfile_whenProfileDoesNotExist_createsProfileAndUserInputSkills() {
        Instant savedAt = Instant.parse("2026-04-29T01:00:00Z");
        JobRole jobRole = jobRoleWithId(100L);
        given(jobRoleRepository.findByRoleCodeAndActiveTrue("BACKEND_ENGINEER")).willReturn(Optional.of(jobRole));
        given(userProfileRepository.findByUserId(1L)).willReturn(Optional.empty());
        given(userProfileRepository.saveAndFlush(any(UserProfile.class)))
                .willAnswer(invocation -> {
                    UserProfile profile = invocation.getArgument(0);
                    setEntityId(profile, 10L);
                    setUpdatedAt(profile, savedAt);
                    return profile;
                });
        given(userSkillRepository.findByUserIdAndSourceTypeOrderBySkillNameAsc(1L, SkillSourceType.USER_INPUT))
                .willReturn(List.of());

        ProfileSaveResult result = profileService.saveProfile(1L, profileRequest("Spring Boot", "PostgreSQL"));

        assertThat(result).isEqualTo(new ProfileSaveResult(10L, savedAt));
        ArgumentCaptor<UserProfile> profileCaptor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).saveAndFlush(profileCaptor.capture());
        UserProfile savedProfile = profileCaptor.getValue();
        assertThat(savedProfile.getUserId()).isEqualTo(1L);
        assertThat(savedProfile.getJobRoleId()).isEqualTo(100L);
        assertThat(savedProfile.getCurrentLevel()).isEqualTo(CurrentLevel.JUNIOR);
        assertThat(savedProfile.getWeeklyStudyHours()).isEqualTo(10);
        assertThat(savedProfile.getTargetDate()).isEqualTo(LocalDate.of(2099, 12, 31));
        assertThat(savedProfile.getInterestAreasJson()).isEqualTo("[\"백엔드\",\"성능 최적화\"]");
        assertThat(savedProfile.getResumeAssetId()).isEqualTo(1001L);
        assertThat(savedProfile.getPortfolioAssetId()).isEqualTo(2001L);

        ArgumentCaptor<List<UserSkill>> skillCaptor = ArgumentCaptor.forClass(List.class);
        verify(userSkillRepository).saveAll(skillCaptor.capture());
        assertThat(skillCaptor.getValue())
                .extracting(UserSkill::getSkillName)
                .containsExactly("Spring Boot", "PostgreSQL");
        assertThat(skillCaptor.getValue())
                .extracting(UserSkill::getSourceType)
                .containsOnly(SkillSourceType.USER_INPUT);
    }

    @Test
    void saveProfile_whenProfileExists_updatesProfileAndReplacesOnlyUserInputSkills() {
        Instant savedAt = Instant.parse("2026-04-29T01:00:00Z");
        JobRole jobRole = jobRoleWithId(100L);
        UserProfile existingProfile = UserProfile.create(
                1L,
                90L,
                CurrentLevel.BASIC,
                5,
                LocalDate.of(2099, 1, 1),
                "[]",
                null,
                null
        );
        setEntityId(existingProfile, 10L);
        List<UserSkill> existingUserInputSkills = List.of(UserSkill.create(
                1L,
                "Old Skill",
                ProficiencyLevel.BASIC,
                SkillSourceType.USER_INPUT
        ));
        given(jobRoleRepository.findByRoleCodeAndActiveTrue("BACKEND_ENGINEER")).willReturn(Optional.of(jobRole));
        given(userProfileRepository.findByUserId(1L)).willReturn(Optional.of(existingProfile));
        given(userProfileRepository.saveAndFlush(existingProfile)).willAnswer(invocation -> {
            setUpdatedAt(existingProfile, savedAt);
            return existingProfile;
        });
        given(userSkillRepository.findByUserIdAndSourceTypeOrderBySkillNameAsc(1L, SkillSourceType.USER_INPUT))
                .willReturn(existingUserInputSkills);

        ProfileSaveResult result = profileService.saveProfile(1L, profileRequest("Redis"));

        assertThat(result).isEqualTo(new ProfileSaveResult(10L, savedAt));
        assertThat(existingProfile.getJobRoleId()).isEqualTo(100L);
        assertThat(existingProfile.getCurrentLevel()).isEqualTo(CurrentLevel.JUNIOR);
        verify(userSkillRepository).deleteAll(existingUserInputSkills);
        verify(userSkillRepository).flush();
        ArgumentCaptor<List<UserSkill>> skillCaptor = ArgumentCaptor.forClass(List.class);
        verify(userSkillRepository).saveAll(skillCaptor.capture());
        assertThat(skillCaptor.getValue())
                .extracting(UserSkill::getSkillName)
                .containsExactly("Redis");
    }

    @Test
    void saveProfile_whenTargetRoleDoesNotExist_throwsInvalidInput() {
        given(jobRoleRepository.findByRoleCodeAndActiveTrue("UNKNOWN")).willReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.saveProfile(1L, profileRequestWithTargetRole("UNKNOWN", "Redis")))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verifyNoInteractions(userProfileRepository, userSkillRepository);
    }

    @Test
    void saveProfile_whenSkillNamesAreDuplicatedAfterNormalization_throwsInvalidInput() {
        JobRole jobRole = mock(JobRole.class);
        given(jobRoleRepository.findByRoleCodeAndActiveTrue("BACKEND_ENGINEER")).willReturn(Optional.of(jobRole));

        assertThatThrownBy(() -> profileService.saveProfile(1L, profileRequest(" Redis ", "redis")))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verifyNoInteractions(userProfileRepository, userSkillRepository);
    }

    @Test
    void findMyProfile_whenProfileExists_returnsUserInputProfileDetail() {
        UserProfile profile = UserProfile.create(
                1L,
                100L,
                CurrentLevel.JUNIOR,
                10,
                LocalDate.of(2099, 12, 31),
                "[\"백엔드\",\"성능 최적화\"]",
                1001L,
                2001L
        );
        setEntityId(profile, 10L);
        JobRole jobRole = jobRoleWithRoleCode("BACKEND_ENGINEER");
        given(userProfileRepository.findByUserId(1L)).willReturn(Optional.of(profile));
        given(jobRoleRepository.findById(100L)).willReturn(Optional.of(jobRole));
        given(userSkillRepository.findByUserIdAndSourceTypeOrderBySkillNameAsc(1L, SkillSourceType.USER_INPUT))
                .willReturn(List.of(UserSkill.create(
                        1L,
                        "Spring Boot",
                        ProficiencyLevel.WORKING,
                        SkillSourceType.USER_INPUT
                )));

        ProfileDetailResponse result = profileService.findMyProfile(1L);

        assertThat(result.profileId()).isEqualTo("10");
        assertThat(result.targetRole()).isEqualTo("BACKEND_ENGINEER");
        assertThat(result.currentLevel()).isEqualTo(CurrentLevel.JUNIOR);
        assertThat(result.skills()).hasSize(1);
        assertThat(result.skills().get(0).sourceType()).isEqualTo(SkillSourceType.USER_INPUT);
        assertThat(result.interestAreas()).containsExactly("백엔드", "성능 최적화");
        assertThat(result.resumeAssetId()).isEqualTo("1001");
        assertThat(result.portfolioAssetId()).isEqualTo("2001");
    }

    @Test
    void findMyProfile_whenProfileDoesNotExist_throwsResourceNotFound() {
        given(userProfileRepository.findByUserId(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.findMyProfile(1L))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        verifyNoMoreInteractions(userProfileRepository);
        verifyNoInteractions(jobRoleRepository, userSkillRepository);
    }

    private ProfileSaveRequest profileRequest(String... skillNames) {
        return profileRequestWithTargetRole("BACKEND_ENGINEER", skillNames);
    }

    private ProfileSaveRequest profileRequestWithTargetRole(String targetRole, String... skillNames) {
        return new ProfileSaveRequest(
                targetRole,
                CurrentLevel.JUNIOR,
                List.of(skillNames).stream()
                        .map(skillName -> new ProfileSkillRequest(skillName, ProficiencyLevel.WORKING))
                        .toList(),
                List.of("백엔드", "성능 최적화"),
                10,
                LocalDate.of(2099, 12, 31),
                1001L,
                2001L
        );
    }

    private JobRole jobRoleWithId(Long id) {
        JobRole jobRole = mock(JobRole.class);
        given(jobRole.getId()).willReturn(id);
        return jobRole;
    }

    private JobRole jobRoleWithRoleCode(String roleCode) {
        JobRole jobRole = mock(JobRole.class);
        given(jobRole.getRoleCode()).willReturn(roleCode);
        return jobRole;
    }

    private void setEntityId(Object entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
    }

    private void setUpdatedAt(Object entity, Instant updatedAt) {
        ReflectionTestUtils.setField(entity, "updatedAt", updatedAt);
    }
}
