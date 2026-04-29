package com.back.coach.domain.dashboard.controller;

import com.back.coach.domain.dashboard.dto.DashboardSnapshot;
import com.back.coach.domain.dashboard.service.DashboardSnapshotService;
import com.back.coach.domain.github.dto.GithubAnalysisPayload;
import com.back.coach.global.code.CurrentLevel;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Mock
    private DashboardSnapshotService dashboardSnapshotService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DashboardController(dashboardSnapshotService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void findDashboard_whenLatestResultsExist_returnsDashboardSnapshot() throws Exception {
        Instant profileUpdatedAt = Instant.parse("2026-04-28T01:00:00Z");
        Instant githubAnalysisCreatedAt = Instant.parse("2026-04-28T02:00:00Z");
        Instant diagnosisCreatedAt = Instant.parse("2026-04-28T03:00:00Z");
        Instant roadmapCreatedAt = Instant.parse("2026-04-28T04:00:00Z");
        given(dashboardSnapshotService.findSnapshot(1L))
                .willReturn(new DashboardSnapshot(
                        1L,
                        new DashboardSnapshot.ProfileSummary(
                                10L,
                                100L,
                                CurrentLevel.JUNIOR,
                                12,
                                LocalDate.of(2026, 12, 31),
                                profileUpdatedAt
                        ),
                        new DashboardSnapshot.GithubAnalysisSummary(
                                20L,
                                3,
                                "latest analysis",
                                githubAnalysisCreatedAt,
                                new GithubAnalysisPayload.FinalTechProfile(
                                        List.of("Java", "Spring Boot", "Redis"),
                                        List.of("백엔드", "성능 최적화")
                                ),
                                1
                        ),
                        new DashboardSnapshot.DiagnosisSummary(
                                30L,
                                4,
                                "latest diagnosis",
                                diagnosisCreatedAt
                        ),
                        new DashboardSnapshot.RoadmapSummary(
                                40L,
                                2,
                                8,
                                "latest roadmap",
                                roadmapCreatedAt,
                                new DashboardSnapshot.ProgressSummary(4, 1, 1, 1, 1)
                        )
                ));

        mockMvc.perform(get("/api/dashboard")
                        .principal(authentication(1L))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("1"))
                .andExpect(jsonPath("$.data.profile.profileId").value("10"))
                .andExpect(jsonPath("$.data.profile.jobRoleId").value("100"))
                .andExpect(jsonPath("$.data.profile.currentLevel").value("JUNIOR"))
                .andExpect(jsonPath("$.data.profile.weeklyStudyHours").value(12))
                .andExpect(jsonPath("$.data.profile.targetDate").value("2026-12-31"))
                .andExpect(jsonPath("$.data.profile.updatedAt").value("2026-04-28T01:00:00Z"))
                .andExpect(jsonPath("$.data.githubAnalysis.githubAnalysisId").value("20"))
                .andExpect(jsonPath("$.data.githubAnalysis.version").value(3))
                .andExpect(jsonPath("$.data.githubAnalysis.summary").value("latest analysis"))
                .andExpect(jsonPath("$.data.githubAnalysis.createdAt").value("2026-04-28T02:00:00Z"))
                .andExpect(jsonPath("$.data.githubAnalysis.finalTechProfile.confirmedSkills[0]").value("Java"))
                .andExpect(jsonPath("$.data.githubAnalysis.finalTechProfile.confirmedSkills[2]").value("Redis"))
                .andExpect(jsonPath("$.data.githubAnalysis.finalTechProfile.focusAreas[0]").value("백엔드"))
                .andExpect(jsonPath("$.data.githubAnalysis.userCorrectionCount").value(1))
                .andExpect(jsonPath("$.data.diagnosis.diagnosisId").value("30"))
                .andExpect(jsonPath("$.data.diagnosis.version").value(4))
                .andExpect(jsonPath("$.data.diagnosis.summary").value("latest diagnosis"))
                .andExpect(jsonPath("$.data.diagnosis.createdAt").value("2026-04-28T03:00:00Z"))
                .andExpect(jsonPath("$.data.roadmap.roadmapId").value("40"))
                .andExpect(jsonPath("$.data.roadmap.version").value(2))
                .andExpect(jsonPath("$.data.roadmap.totalWeeks").value(8))
                .andExpect(jsonPath("$.data.roadmap.summary").value("latest roadmap"))
                .andExpect(jsonPath("$.data.roadmap.createdAt").value("2026-04-28T04:00:00Z"))
                .andExpect(jsonPath("$.data.roadmap.progress.totalWeeks").value(4))
                .andExpect(jsonPath("$.data.roadmap.progress.todoWeeks").value(1))
                .andExpect(jsonPath("$.data.roadmap.progress.inProgressWeeks").value(1))
                .andExpect(jsonPath("$.data.roadmap.progress.doneWeeks").value(1))
                .andExpect(jsonPath("$.data.roadmap.progress.skippedWeeks").value(1))
                .andExpect(jsonPath("$.meta").isMap());

        verify(dashboardSnapshotService).findSnapshot(1L);
    }

    @Test
    void findDashboard_whenLatestResultsDoNotExist_returnsNullSummaries() throws Exception {
        given(dashboardSnapshotService.findSnapshot(1L))
                .willReturn(new DashboardSnapshot(1L, null, null, null, null));

        mockMvc.perform(get("/api/dashboard")
                        .principal(authentication(1L))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("1"))
                .andExpect(jsonPath("$.data.profile").value(nullValue()))
                .andExpect(jsonPath("$.data.githubAnalysis").value(nullValue()))
                .andExpect(jsonPath("$.data.diagnosis").value(nullValue()))
                .andExpect(jsonPath("$.data.roadmap").value(nullValue()))
                .andExpect(jsonPath("$.meta").isMap());

        verify(dashboardSnapshotService).findSnapshot(1L);
    }

    private Authentication authentication(Long userId) {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(userId), null);
    }
}
