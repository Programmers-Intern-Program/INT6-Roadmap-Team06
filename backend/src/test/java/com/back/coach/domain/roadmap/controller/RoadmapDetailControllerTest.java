package com.back.coach.domain.roadmap.controller;

import com.back.coach.domain.roadmap.dto.RoadmapDetailSnapshot;
import com.back.coach.domain.roadmap.service.RoadmapDetailSnapshotService;
import com.back.coach.global.code.ProgressStatus;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.GlobalExceptionHandler;
import com.back.coach.global.exception.ServiceException;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RoadmapDetailControllerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Mock
    private RoadmapDetailSnapshotService roadmapDetailSnapshotService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RoadmapDetailController(roadmapDetailSnapshotService, objectMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void findRoadmap_whenRoadmapExists_returnsRoadmapDetailWithLatestProgress() throws Exception {
        Instant roadmapCreatedAt = Instant.parse("2026-04-28T01:00:00Z");
        Instant progressUpdatedAt = Instant.parse("2026-04-28T02:00:00Z");
        given(roadmapDetailSnapshotService.findSnapshot(1L, 10L))
                .willReturn(new RoadmapDetailSnapshot(
                        10L,
                        2,
                        2,
                        "Redis 중심 로드맵",
                        roadmapCreatedAt,
                        List.of(new RoadmapDetailSnapshot.WeekSnapshot(
                                100L,
                                1,
                                "Redis 기초",
                                "캐시 설계 경험 보완",
                                "[{\"type\":\"READ_DOCS\",\"title\":\"Redis 공식 문서 읽기\"}]",
                                "[{\"type\":\"DOCS\",\"title\":\"Redis Documentation\",\"url\":\"https://redis.io/docs\"}]",
                                new BigDecimal("8.0"),
                                ProgressStatus.DONE,
                                "TTL 정리 완료",
                                progressUpdatedAt
                        ))
                ));

        mockMvc.perform(get("/api/roadmaps/{roadmapId}", 10L)
                        .principal(authentication(1L))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roadmapId").value("10"))
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.totalWeeks").value(2))
                .andExpect(jsonPath("$.data.summary").value("Redis 중심 로드맵"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-04-28T01:00:00Z"))
                .andExpect(jsonPath("$.data.weeks[0].roadmapWeekId").value("100"))
                .andExpect(jsonPath("$.data.weeks[0].weekNumber").value(1))
                .andExpect(jsonPath("$.data.weeks[0].topic").value("Redis 기초"))
                .andExpect(jsonPath("$.data.weeks[0].reason").value("캐시 설계 경험 보완"))
                .andExpect(jsonPath("$.data.weeks[0].tasks[0].type").value("READ_DOCS"))
                .andExpect(jsonPath("$.data.weeks[0].tasks[0].title").value("Redis 공식 문서 읽기"))
                .andExpect(jsonPath("$.data.weeks[0].materials[0].type").value("DOCS"))
                .andExpect(jsonPath("$.data.weeks[0].materials[0].title").value("Redis Documentation"))
                .andExpect(jsonPath("$.data.weeks[0].materials[0].url").value("https://redis.io/docs"))
                .andExpect(jsonPath("$.data.weeks[0].estimatedHours").value(8.0))
                .andExpect(jsonPath("$.data.weeks[0].progressStatus").value("DONE"))
                .andExpect(jsonPath("$.data.weeks[0].progressNote").value("TTL 정리 완료"))
                .andExpect(jsonPath("$.data.weeks[0].progressUpdatedAt").value("2026-04-28T02:00:00Z"))
                .andExpect(jsonPath("$.meta").isMap());

        verify(roadmapDetailSnapshotService).findSnapshot(1L, 10L);
    }

    @Test
    void findRoadmap_whenRoadmapDoesNotBelongToUser_returnsNotFound() throws Exception {
        given(roadmapDetailSnapshotService.findSnapshot(1L, 10L))
                .willThrow(new ServiceException(ErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(get("/api/roadmaps/{roadmapId}", 10L)
                        .principal(authentication(1L))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void findRoadmap_whenStoredWeekJsonIsInvalid_returnsInternalServerError() throws Exception {
        given(roadmapDetailSnapshotService.findSnapshot(1L, 10L))
                .willReturn(new RoadmapDetailSnapshot(
                        10L,
                        1,
                        1,
                        "잘못된 주차 JSON",
                        Instant.parse("2026-04-28T01:00:00Z"),
                        List.of(new RoadmapDetailSnapshot.WeekSnapshot(
                                100L,
                                1,
                                "Redis 기초",
                                "캐시 설계 경험 보완",
                                "not-json",
                                "[]",
                                new BigDecimal("8.0"),
                                ProgressStatus.TODO,
                                null,
                                null
                        ))
                ));

        mockMvc.perform(get("/api/roadmaps/{roadmapId}", 10L)
                        .principal(authentication(1L))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }

    private Authentication authentication(Long userId) {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(userId), null);
    }
}
