package com.back.coach.domain.user.controller;

import com.back.coach.domain.user.dto.ProfileDetailResponse;
import com.back.coach.domain.user.dto.ProfileSaveRequest;
import com.back.coach.domain.user.dto.ProfileSaveResult;
import com.back.coach.domain.user.dto.ProfileSkillResponse;
import com.back.coach.domain.user.service.ProfileService;
import com.back.coach.global.code.CurrentLevel;
import com.back.coach.global.code.ProficiencyLevel;
import com.back.coach.global.code.SkillSourceType;
import com.back.coach.global.exception.GlobalExceptionHandler;
import com.back.coach.global.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Mock
    private ProfileService profileService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ProfileController(profileService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void saveProfile_whenValidRequest_returnsSaveResponse() throws Exception {
        Instant savedAt = Instant.parse("2026-04-29T01:00:00Z");
        given(profileService.saveProfile(eq(1L), any(ProfileSaveRequest.class)))
                .willReturn(new ProfileSaveResult(10L, savedAt));

        mockMvc.perform(post("/api/profiles")
                        .principal(authentication(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetRole": "BACKEND_ENGINEER",
                                  "currentLevel": "JUNIOR",
                                  "skills": [
                                    {
                                      "skillName": "Spring Boot",
                                      "proficiencyLevel": "WORKING"
                                    }
                                  ],
                                  "interestAreas": ["백엔드", "성능 최적화"],
                                  "weeklyStudyHours": 10,
                                  "targetDate": "2099-12-31",
                                  "resumeAssetId": "1001",
                                  "portfolioAssetId": "2001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileId").value("10"))
                .andExpect(jsonPath("$.data.savedAt").value("2026-04-29T01:00:00Z"))
                .andExpect(jsonPath("$.data.message").value("프로필이 저장되었습니다."))
                .andExpect(jsonPath("$.meta").isMap());

        verify(profileService).saveProfile(eq(1L), any(ProfileSaveRequest.class));
    }

    @Test
    void findMyProfile_whenProfileExists_returnsProfileDetail() throws Exception {
        given(profileService.findMyProfile(1L))
                .willReturn(new ProfileDetailResponse(
                        "10",
                        "BACKEND_ENGINEER",
                        CurrentLevel.JUNIOR,
                        List.of(new ProfileSkillResponse(
                                "Spring Boot",
                                ProficiencyLevel.WORKING,
                                SkillSourceType.USER_INPUT
                        )),
                        List.of("백엔드", "성능 최적화"),
                        10,
                        LocalDate.of(2099, 12, 31),
                        "1001",
                        "2001"
                ));

        mockMvc.perform(get("/api/profiles/me")
                        .principal(authentication(1L))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileId").value("10"))
                .andExpect(jsonPath("$.data.targetRole").value("BACKEND_ENGINEER"))
                .andExpect(jsonPath("$.data.currentLevel").value("JUNIOR"))
                .andExpect(jsonPath("$.data.skills[0].skillName").value("Spring Boot"))
                .andExpect(jsonPath("$.data.skills[0].proficiencyLevel").value("WORKING"))
                .andExpect(jsonPath("$.data.skills[0].sourceType").value("USER_INPUT"))
                .andExpect(jsonPath("$.data.interestAreas[0]").value("백엔드"))
                .andExpect(jsonPath("$.data.weeklyStudyHours").value(10))
                .andExpect(jsonPath("$.data.targetDate").value("2099-12-31"))
                .andExpect(jsonPath("$.data.resumeAssetId").value("1001"))
                .andExpect(jsonPath("$.data.portfolioAssetId").value("2001"))
                .andExpect(jsonPath("$.meta").isMap());

        verify(profileService).findMyProfile(1L);
    }

    @Test
    void saveProfile_whenTargetRoleMissing_returnsInvalidInput() throws Exception {
        mockMvc.perform(post("/api/profiles")
                        .principal(authentication(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentLevel": "JUNIOR",
                                  "skills": [
                                    {
                                      "skillName": "Spring Boot"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(profileService);
    }

    @Test
    void saveProfile_whenSkillsEmpty_returnsInvalidInput() throws Exception {
        mockMvc.perform(post("/api/profiles")
                        .principal(authentication(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetRole": "BACKEND_ENGINEER",
                                  "currentLevel": "JUNIOR",
                                  "skills": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(profileService);
    }

    @Test
    void saveProfile_whenWeeklyStudyHoursOutOfRange_returnsInvalidInput() throws Exception {
        mockMvc.perform(post("/api/profiles")
                        .principal(authentication(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetRole": "BACKEND_ENGINEER",
                                  "currentLevel": "JUNIOR",
                                  "skills": [
                                    {
                                      "skillName": "Spring Boot"
                                    }
                                  ],
                                  "weeklyStudyHours": 41
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(profileService);
    }

    private Authentication authentication(Long userId) {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(userId), null);
    }
}
