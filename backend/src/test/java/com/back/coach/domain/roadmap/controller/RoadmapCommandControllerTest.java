package com.back.coach.domain.roadmap.controller;

import com.back.coach.domain.roadmap.dto.RoadmapDetailResponse;
import com.back.coach.domain.roadmap.dto.RoadmapRequest;
import com.back.coach.domain.roadmap.service.RoadmapCommandService;
import com.back.coach.domain.user.entity.User;
import com.back.coach.domain.user.repository.UserRepository;
import com.back.coach.global.code.AuthProvider;
import com.back.coach.global.code.ProgressStatus;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.back.coach.global.security.JwtTokenProvider;
import com.back.coach.support.ApiTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoadmapCommandControllerTest extends ApiTestBase {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RoadmapCommandService roadmapCommandService;

    @Test
    void createRoadmap_whenRequestIsValid_returnsRoadmapDetail() throws Exception {
        User user = userRepository.save(User.signupFromOAuth(AuthProvider.GITHUB, "gh-roadmap-1", "roadmap1@example.com"));
        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        given(roadmapCommandService.createRoadmap(eq(user.getId()), any(RoadmapRequest.class)))
                .willReturn(response());

        mockMvc.perform(post("/api/roadmaps")
                        .cookie(new Cookie("accessToken", accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "diagnosisId", 20,
                                "githubAnalysisId", 30,
                                "weeklyStudyHours", 8
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roadmapId").value("40"))
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.totalWeeks").value(2))
                .andExpect(jsonPath("$.data.summary").value("Redis 중심 로드맵"))
                .andExpect(jsonPath("$.data.weeks[0].roadmapWeekId").value("100"))
                .andExpect(jsonPath("$.data.weeks[0].weekNumber").value(1))
                .andExpect(jsonPath("$.data.weeks[0].topic").value("Redis 기초"))
                .andExpect(jsonPath("$.data.weeks[0].progressStatus").value("TODO"))
                .andExpect(jsonPath("$.meta").isMap());
    }

    @Test
    void createRoadmap_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/roadmaps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diagnosisId\":20}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createRoadmap_whenDiagnosisIdIsMissing_returnsBadRequest() throws Exception {
        User user = userRepository.save(User.signupFromOAuth(AuthProvider.GITHUB, "gh-roadmap-2", "roadmap2@example.com"));
        String accessToken = jwtTokenProvider.createAccessToken(user.getId());

        mockMvc.perform(post("/api/roadmaps")
                        .cookie(new Cookie("accessToken", accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRoadmap_whenDiagnosisDoesNotExist_returnsNotFound() throws Exception {
        User user = userRepository.save(User.signupFromOAuth(AuthProvider.GITHUB, "gh-roadmap-3", "roadmap3@example.com"));
        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        given(roadmapCommandService.createRoadmap(eq(user.getId()), any(RoadmapRequest.class)))
                .willThrow(new ServiceException(ErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(post("/api/roadmaps")
                        .cookie(new Cookie("accessToken", accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diagnosisId\":999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private RoadmapDetailResponse response() {
        return new RoadmapDetailResponse(
                "40",
                2,
                2,
                "Redis 중심 로드맵",
                Instant.parse("2026-04-30T01:00:00Z"),
                List.of(new RoadmapDetailResponse.WeekResponse(
                        "100",
                        1,
                        "Redis 기초",
                        "캐시 설계 역량을 먼저 보완해야 함",
                        List.of(Map.of(
                                "type", "READ_DOCS",
                                "title", "Redis 공식 문서 읽기"
                        )),
                        List.of(Map.of(
                                "type", "DOCS",
                                "title", "Redis Documentation",
                                "url", "https://redis.io/docs"
                        )),
                        new BigDecimal("8.0"),
                        ProgressStatus.TODO,
                        null,
                        null
                ))
        );
    }
}
