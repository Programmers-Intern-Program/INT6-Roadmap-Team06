package com.back.coach.domain.github.controller;

import com.back.coach.domain.github.dto.GithubAnalysisPayload;
import com.back.coach.domain.github.entity.GithubAnalysis;
import com.back.coach.domain.github.entity.GithubConnection;
import com.back.coach.domain.github.repository.GithubAnalysisRepository;

import com.back.coach.domain.github.service.GithubAnalysisPayloadJson;
import com.back.coach.domain.github.service.GithubAnalysisService;
import com.back.coach.domain.user.entity.User;
import com.back.coach.domain.user.repository.UserRepository;
import com.back.coach.global.code.AuthProvider;
import com.back.coach.domain.github.repository.GithubConnectionRepository;
import com.back.coach.global.code.GithubAccessType;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.back.coach.global.security.JwtTokenProvider;
import com.back.coach.support.ApiTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GithubAnalysisControllerTest extends ApiTestBase {

    @Autowired UserRepository userRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired ObjectMapper objectMapper;
    @Autowired GithubAnalysisRepository analysisRepository;
    @Autowired GithubConnectionRepository connectionRepository;
    @Autowired GithubAnalysisPayloadJson payloadJson;

    @MockitoBean GithubAnalysisService analysisService;

    @Test
    @DisplayName("POST /api/github-analyses — 정상 요청은 200 + 본문에 분석 결과")
    void post_happyPath() throws Exception {
        User user = userRepository.save(User.signupFromOAuth(AuthProvider.GITHUB, "gh-an-1", "an1@example.com"));
        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        GithubAnalysisPayload payload = samplePayload();
        given(analysisService.run(eqUser(user.getId()), eqLong(50L), any(), any()))
                .willReturn(new GithubAnalysisService.GithubAnalysisResult(
                        77L, 1, payload, "확정 스킬: Spring Boot", Instant.parse("2026-04-28T00:00:00Z"),
                        new GithubAnalysisService.AnalysisMetrics(5000L, 2, 1000, 2000L, 500, 1500L, 800, 1500L)));

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
                .andExpect(jsonPath("$.data.githubAnalysisId").value("77"))
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

    // ── PATCH /api/github-analyses/{id}/corrections ──

    @Test
    @DisplayName("PATCH /corrections — 인증 없이 호출 시 401")
    void patch_withoutAuth_unauthorized() throws Exception {
        mockMvc.perform(patch("/api/github-analyses/1/corrections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userCorrections\":[],\"finalTechProfile\":{\"confirmedSkills\":[],\"focusAreas\":[]}}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PATCH /corrections — finalTechProfile 누락이면 400")
    void patch_nullFinalTechProfile_badRequest() throws Exception {
        User user = userRepository.save(User.signupFromOAuth(AuthProvider.GITHUB, "gh-an-6", "an6@example.com"));
        String accessToken = jwtTokenProvider.createAccessToken(user.getId());

        mockMvc.perform(patch("/api/github-analyses/1/corrections")
                        .cookie(new Cookie("accessToken", accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userCorrections\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /corrections — 존재하지 않는 분석 id는 404")
    void patch_nonexistentId_notFound() throws Exception {
        User user = userRepository.save(User.signupFromOAuth(AuthProvider.GITHUB, "gh-an-7", "an7@example.com"));
        String accessToken = jwtTokenProvider.createAccessToken(user.getId());

        mockMvc.perform(patch("/api/github-analyses/9999/corrections")
                        .cookie(new Cookie("accessToken", accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userCorrections\":[],\"finalTechProfile\":{\"confirmedSkills\":[],\"focusAreas\":[]}}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH /corrections — 정상 요청은 200 + savedAt + updatedFinalTechProfile 반환")
    void patch_happyPath() throws Exception {
        User user = userRepository.save(User.signupFromOAuth(AuthProvider.GITHUB, "gh-an-8", "an8@example.com"));
        String accessToken = jwtTokenProvider.createAccessToken(user.getId());

        // GithubAnalysis 직접 삽입
        GithubConnection conn = saveConnection(user.getId());
        GithubAnalysis analysis = GithubAnalysis.create(user.getId(), conn.getId(), 1, "summary",
                payloadJson.toJson(samplePayload()));
        GithubAnalysis saved = analysisRepository.save(analysis);

        String body = """
                {
                  "userCorrections": [{"skillName": "Spring Boot", "correction": "백엔드에서만 사용"}],
                  "finalTechProfile": {"confirmedSkills": ["Spring Boot", "JPA"], "focusAreas": ["Backend"]}
                }
                """;

        mockMvc.perform(patch("/api/github-analyses/" + saved.getId() + "/corrections")
                        .cookie(new Cookie("accessToken", accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.githubAnalysisId").value(String.valueOf(saved.getId())))
                .andExpect(jsonPath("$.data.savedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.finalTechProfile.confirmedSkills[0]").value("Spring Boot"))
                .andExpect(jsonPath("$.data.finalTechProfile.confirmedSkills[1]").value("JPA"))
                .andExpect(jsonPath("$.data.finalTechProfile.focusAreas[0]").value("Backend"));

        // DB에서 corrections이 실제로 저장됐는지 검증
        GithubAnalysis updated = analysisRepository.findByIdAndUserId(saved.getId(), user.getId()).orElseThrow();
        GithubAnalysisPayload updatedPayload = payloadJson.fromJson(updated.getAnalysisPayload());
        assertThat(updatedPayload.userCorrections())
                .extracting(GithubAnalysisPayload.GithubUserCorrection::skillName)
                .containsExactly("Spring Boot");
        assertThat(updatedPayload.finalTechProfile().confirmedSkills()).containsExactly("Spring Boot", "JPA");

        mockMvc.perform(get("/api/github-analyses/" + saved.getId())
                        .cookie(new Cookie("accessToken", accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userCorrections[0].skillName").value("Spring Boot"))
                .andExpect(jsonPath("$.data.userCorrections[0].correction").value("백엔드에서만 사용"))
                .andExpect(jsonPath("$.data.finalTechProfile.confirmedSkills[0]").value("Spring Boot"))
                .andExpect(jsonPath("$.data.finalTechProfile.confirmedSkills[1]").value("JPA"))
                .andExpect(jsonPath("$.data.finalTechProfile.focusAreas[0]").value("Backend"));
    }

    @Test
    @DisplayName("GET /api/github-analyses/{id} — 다른 사용자의 분석은 404")
    void get_otherUserAnalysis_notFound() throws Exception {
        User owner = userRepository.save(User.signupFromOAuth(AuthProvider.GITHUB, "gh-an-owner-get", "owner-get@example.com"));
        User other = userRepository.save(User.signupFromOAuth(AuthProvider.GITHUB, "gh-an-other-get", "other-get@example.com"));
        String otherAccessToken = jwtTokenProvider.createAccessToken(other.getId());
        GithubConnection conn = saveConnection(owner.getId());
        GithubAnalysis saved = analysisRepository.save(GithubAnalysis.create(
                owner.getId(),
                conn.getId(),
                1,
                "summary",
                payloadJson.toJson(samplePayload())
        ));

        mockMvc.perform(get("/api/github-analyses/" + saved.getId())
                        .cookie(new Cookie("accessToken", otherAccessToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("PATCH /corrections — 다른 사용자의 분석은 수정할 수 없다")
    void patch_otherUserAnalysis_notFound() throws Exception {
        User owner = userRepository.save(User.signupFromOAuth(AuthProvider.GITHUB, "gh-an-owner-patch", "owner-patch@example.com"));
        User other = userRepository.save(User.signupFromOAuth(AuthProvider.GITHUB, "gh-an-other-patch", "other-patch@example.com"));
        String otherAccessToken = jwtTokenProvider.createAccessToken(other.getId());
        GithubConnection conn = saveConnection(owner.getId());
        GithubAnalysis saved = analysisRepository.save(GithubAnalysis.create(
                owner.getId(),
                conn.getId(),
                1,
                "summary",
                payloadJson.toJson(samplePayload())
        ));

        mockMvc.perform(patch("/api/github-analyses/" + saved.getId() + "/corrections")
                        .cookie(new Cookie("accessToken", otherAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userCorrections": [{"skillName": "Spring Boot", "correction": "다른 사용자 수정"}],
                                  "finalTechProfile": {"confirmedSkills": ["Spring Boot"], "focusAreas": ["Backend"]}
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private GithubConnection saveConnection(Long userId) {
        return connectionRepository.save(
                GithubConnection.connect(userId, "gh-uid-" + userId, "testuser-" + userId, GithubAccessType.OAUTH, "ghp_test"));
    }

    private static GithubAnalysisPayload samplePayload() {
        return new GithubAnalysisPayload(
                new GithubAnalysisPayload.StaticSignals(List.of(), 1, "WEEKLY", "CONSISTENT"),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                new GithubAnalysisPayload.FinalTechProfile(List.of("Spring Boot"), List.of())
        );
    }

    private static Long eqUser(Long id) { return org.mockito.ArgumentMatchers.eq(id); }
    private static Long eqLong(long v) { return org.mockito.ArgumentMatchers.eq(v); }
}
