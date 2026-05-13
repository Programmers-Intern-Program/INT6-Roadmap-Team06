package com.back.coach.domain.portfolio.controller;

import com.back.coach.domain.portfolio.dto.PortfolioDraftDetailResponse;
import com.back.coach.domain.portfolio.dto.PortfolioDraftPayload;
import com.back.coach.domain.portfolio.dto.PortfolioDraftSummaryResponse;
import com.back.coach.domain.portfolio.dto.PortfolioDraftUpdateRequest;
import com.back.coach.domain.portfolio.dto.PortfolioDraftVariant;
import com.back.coach.domain.portfolio.service.PortfolioDraftService;
import com.back.coach.domain.user.entity.User;
import com.back.coach.domain.user.repository.UserRepository;
import com.back.coach.global.code.AuthProvider;
import com.back.coach.global.security.JwtTokenProvider;
import com.back.coach.support.ApiTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PortfolioDraftControllerTest extends ApiTestBase {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PortfolioDraftService portfolioDraftService;

    @Test
    void createDraft_whenAuthenticated_returnsSavedDraft() throws Exception {
        User user = createUser();
        given(portfolioDraftService.createDraft(user.getId())).willReturn(detailResponse("10"));

        mockMvc.perform(post("/api/portfolio/drafts")
                        .cookie(accessTokenCookie(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draftId").value("10"))
                .andExpect(jsonPath("$.data.title").value("백엔드 성장 포트폴리오 초안"))
                .andExpect(jsonPath("$.data.draftPayload.variants[0].key").value("DONE"))
                .andExpect(jsonPath("$.data.sourceRefs.progressLogIds[0]").value("1"));
    }

    @Test
    void listDrafts_whenAuthenticated_returnsSummaries() throws Exception {
        User user = createUser();
        given(portfolioDraftService.listDrafts(user.getId())).willReturn(List.of(
                new PortfolioDraftSummaryResponse(
                        "10",
                        "백엔드 성장 포트폴리오 초안",
                        Instant.parse("2026-05-13T00:00:00Z"),
                        Instant.parse("2026-05-13T01:00:00Z")
                )
        ));

        mockMvc.perform(get("/api/portfolio/drafts")
                        .cookie(accessTokenCookie(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].draftId").value("10"))
                .andExpect(jsonPath("$.data[0].title").value("백엔드 성장 포트폴리오 초안"));
    }

    @Test
    void updateDraft_whenRequestIsValid_returnsUpdatedDraft() throws Exception {
        User user = createUser();
        given(portfolioDraftService.updateDraft(eq(user.getId()), eq(10L), any(PortfolioDraftUpdateRequest.class)))
                .willReturn(detailResponse("10"));

        mockMvc.perform(patch("/api/portfolio/drafts/{draftId}", 10)
                        .cookie(accessTokenCookie(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PortfolioDraftUpdateRequest(
                                "수정된 초안",
                                new PortfolioDraftPayload(
                                        "PROJECT_WRITEUP",
                                        List.of(
                                                new PortfolioDraftVariant("DONE", "완료 기반", "수정된 내용"),
                                                new PortfolioDraftVariant("DONE_IN_PROGRESS", "완료 + 진행 중", "수정된 진행 내용"),
                                                new PortfolioDraftVariant("ALL", "전체 계획 포함", "수정된 전체 내용")
                                        )
                                )
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draftId").value("10"))
                .andExpect(jsonPath("$.data.draftPayload.format").value("PROJECT_WRITEUP"));
    }

    @Test
    void createDraft_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/portfolio/drafts"))
                .andExpect(status().isUnauthorized());
    }

    private User createUser() {
        return userRepository.save(User.signupFromOAuth(
                AuthProvider.GITHUB,
                "portfolio-api-gh-" + UUID.randomUUID(),
                "portfolio-api-" + UUID.randomUUID() + "@test.com"
        ));
    }

    private Cookie accessTokenCookie(User user) {
        return new Cookie("accessToken", jwtTokenProvider.createAccessToken(user.getId()));
    }

    private PortfolioDraftDetailResponse detailResponse(String draftId) throws Exception {
        return new PortfolioDraftDetailResponse(
                draftId,
                "백엔드 성장 포트폴리오 초안",
                new PortfolioDraftPayload(
                        "PROJECT_WRITEUP",
                        List.of(
                                new PortfolioDraftVariant("DONE", "완료 기반", "완료 내용"),
                                new PortfolioDraftVariant("DONE_IN_PROGRESS", "완료 + 진행 중", "진행 내용"),
                                new PortfolioDraftVariant("ALL", "전체 계획 포함", "전체 내용")
                        )
                ),
                Map.of("progressLogIds", List.of("1")),
                Instant.parse("2026-05-13T00:00:00Z"),
                Instant.parse("2026-05-13T01:00:00Z")
        );
    }
}
