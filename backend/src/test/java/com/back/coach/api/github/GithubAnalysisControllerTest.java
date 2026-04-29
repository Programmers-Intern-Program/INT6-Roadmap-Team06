package com.back.coach.api.github;

import com.back.coach.domain.github.entity.GithubAnalysis;
import com.back.coach.domain.github.repository.GithubAnalysisRepository;
import com.back.coach.domain.user.entity.User;
import com.back.coach.domain.user.repository.UserRepository;
import com.back.coach.global.code.AuthProvider;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.back.coach.global.security.JwtTokenProvider;
import com.back.coach.service.github.AnalysisPayload;
import com.back.coach.service.github.AnalysisPayloadJson;
import com.back.coach.service.github.GithubAnalysisService;
import com.back.coach.support.ApiTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GithubAnalysisControllerTest extends ApiTestBase {

    @Autowired UserRepository userRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired ObjectMapper objectMapper;
    @Autowired AnalysisPayloadJson payloadJson;

    @MockitoBean GithubAnalysisService analysisService;
    @MockitoBean GithubAnalysisRepository analysisRepository;

    @Test
    @DisplayName("POST /api/github-analyses — 정상 요청은 200 + 본문에 분석 결과")
    void post_happyPath() throws Exception {
        User user = userRepository.save(User.signupFromOAuth(AuthProvider.GITHUB, "gh-an-1", "an1@example.com"));
        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        AnalysisPayload payload = samplePayload();
        given(analysisService.run(eqUser(user.getId()), eqLong(50L), any(), any()))
                .willReturn(new GithubAnalysisService.GithubAnalysisResult(
                        77L, 1, payload, "확정 스킬: Spring Boot", Instant.parse("2026-04-28T00:00:00Z")));

        String body = objectMapper.writeValueAsString(Map.of(
                "githubConnectionId", 50,
                "selectedRepositoryIds", List.of(1, 2),
                "coreRepositoryIds", List.of(1)
        ));

        mockMvc.perform(post("/api/github-analyses")
                        .cookie(new Cookie("accessToken", accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.githubAnalysisId").value(77))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.finalTechProfile.confirmedSkills[0]").value("Spring Boot"));
    }

    @Test
    @DisplayName("POST 인증 없이 호출 시 401")
    void post_withoutAuth_unauthorized() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "githubConnectionId", 50, "selectedRepositoryIds", List.of(1), "coreRepositoryIds", List.of(1)));

        mockMvc.perform(post("/api/github-analyses")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST selectedRepositoryIds 비어있으면 400")
    void post_emptySelected_badRequest() throws Exception {
        User user = userRepository.save(User.signupFromOAuth(AuthProvider.GITHUB, "gh-an-2", "an2@example.com"));
        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        String body = objectMapper.writeValueAsString(Map.of(
                "githubConnectionId", 50, "selectedRepositoryIds", List.of(), "coreRepositoryIds", List.of()));

        mockMvc.perform(post("/api/github-analyses")
                        .cookie(new Cookie("accessToken", accessToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST 서비스가 INVALID_INPUT throw하면 400")
    void post_serviceInvalidInput_badRequest() throws Exception {
        User user = userRepository.save(User.signupFromOAuth(AuthProvider.GITHUB, "gh-an-3", "an3@example.com"));
        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        given(analysisService.run(anyLong(), anyLong(), any(), any()))
                .willThrow(new ServiceException(ErrorCode.INVALID_INPUT, "core not subset"));

        String body = objectMapper.writeValueAsString(Map.of(
                "githubConnectionId", 50, "selectedRepositoryIds", List.of(1), "coreRepositoryIds", List.of(2)));

        mockMvc.perform(post("/api/github-analyses")
                        .cookie(new Cookie("accessToken", accessToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /{id} — 본인 분석 정상 조회")
    void get_happyPath() throws Exception {
        User user = userRepository.save(User.signupFromOAuth(AuthProvider.GITHUB, "gh-an-4", "an4@example.com"));
        String accessToken = jwtTokenProvider.createAccessToken(user.getId());

        AnalysisPayload payload = samplePayload();
        GithubAnalysis analysis = GithubAnalysis.create(user.getId(), 50L, 1, "summary", payloadJson.toJson(payload));
        ReflectionTestUtils.setField(analysis, "id", 99L);
        ReflectionTestUtils.setField(analysis, "createdAt", Instant.parse("2026-04-28T00:00:00Z"));
        given(analysisRepository.findByIdAndUserId(99L, user.getId())).willReturn(java.util.Optional.of(analysis));

        mockMvc.perform(get("/api/github-analyses/99")
                        .cookie(new Cookie("accessToken", accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.githubAnalysisId").value(99))
                .andExpect(jsonPath("$.data.version").value(1));
    }

    @Test
    @DisplayName("GET /{id} — 존재하지 않는 분석은 404")
    void get_notFound() throws Exception {
        User user = userRepository.save(User.signupFromOAuth(AuthProvider.GITHUB, "gh-an-5", "an5@example.com"));
        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        given(analysisRepository.findByIdAndUserId(anyLong(), anyLong())).willReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/github-analyses/9999")
                        .cookie(new Cookie("accessToken", accessToken)))
                .andExpect(status().isNotFound());
    }

    private static AnalysisPayload samplePayload() {
        return new AnalysisPayload(
                new AnalysisPayload.StaticSignals(List.of(), 1, "WEEKLY", "CONSISTENT"),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                new AnalysisPayload.FinalTechProfile(List.of("Spring Boot"), List.of()),
                new AnalysisPayload.AnalysisMeta(false)
        );
    }

    // 가독성 helper
    private static Long eqUser(Long id) { return org.mockito.ArgumentMatchers.eq(id); }
    private static Long eqLong(long v) { return org.mockito.ArgumentMatchers.eq(v); }
}
