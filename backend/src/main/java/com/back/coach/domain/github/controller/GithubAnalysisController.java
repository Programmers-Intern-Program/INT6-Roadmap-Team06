package com.back.coach.domain.github.controller;

import com.back.coach.domain.github.dto.GithubAnalysisDetailResponse;
import com.back.coach.domain.github.service.GithubAnalysisDetailService;
import com.back.coach.global.response.ApiResponse;
import com.back.coach.global.security.AuthenticatedUser;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/github-analyses", produces = MediaType.APPLICATION_JSON_VALUE)
public class GithubAnalysisController {

    private final GithubAnalysisDetailService githubAnalysisDetailService;

    public GithubAnalysisController(GithubAnalysisDetailService githubAnalysisDetailService) {
        this.githubAnalysisDetailService = githubAnalysisDetailService;
    }

    @GetMapping("/{githubAnalysisId}")
    public ApiResponse<GithubAnalysisDetailResponse> findAnalysis(
            Authentication authentication,
            @PathVariable Long githubAnalysisId
    ) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();

        return ApiResponse.success(githubAnalysisDetailService.findAnalysis(
                authenticatedUser.userId(),
                githubAnalysisId
        ));
    }
}
