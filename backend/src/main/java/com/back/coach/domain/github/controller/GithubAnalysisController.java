package com.back.coach.domain.github.controller;

import com.back.coach.domain.github.dto.GithubAnalysisCorrectionRequest;
import com.back.coach.domain.github.dto.GithubAnalysisCorrectionResponse;
import com.back.coach.domain.github.dto.GithubAnalysisDetailResponse;
import com.back.coach.domain.github.dto.GithubAnalysisRequest;
import com.back.coach.domain.github.dto.GithubAnalysisResponse;
import com.back.coach.domain.github.dto.GithubAnalysisSummaryResponse;
import com.back.coach.domain.github.service.GithubAnalysisDetailService;
import com.back.coach.domain.github.service.GithubAnalysisService;
import com.back.coach.global.response.ApiResponse;
import com.back.coach.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(path = "/api/github-analyses", produces = MediaType.APPLICATION_JSON_VALUE)
public class GithubAnalysisController {

    private final GithubAnalysisDetailService githubAnalysisDetailService;
    private final GithubAnalysisService analysisService;

    public GithubAnalysisController(GithubAnalysisDetailService githubAnalysisDetailService,
                                    GithubAnalysisService analysisService) {
        this.githubAnalysisDetailService = githubAnalysisDetailService;
        this.analysisService = analysisService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<GithubAnalysisResponse> runAnalysis(
            Authentication authentication,
            @Valid @RequestBody GithubAnalysisRequest request
    ) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();

        GithubAnalysisService.GithubAnalysisResult result = analysisService.run(
                authenticatedUser.userId(),
                request.githubConnectionId(),
                request.selectedRepositoryIds(),
                request.coreRepositoryIds()
        );
        return ApiResponse.success(GithubAnalysisResponse.from(result));
    }

    @GetMapping
    public ApiResponse<List<GithubAnalysisSummaryResponse>> listAnalyses(Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return ApiResponse.success(githubAnalysisDetailService.listByUser(authenticatedUser.userId()));
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

    @PatchMapping(path = "/{githubAnalysisId}/corrections", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<GithubAnalysisCorrectionResponse> saveCorrections(
            Authentication authentication,
            @PathVariable Long githubAnalysisId,
            @Valid @RequestBody GithubAnalysisCorrectionRequest request
    ) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();

        return ApiResponse.success(githubAnalysisDetailService.saveCorrections(
                authenticatedUser.userId(),
                githubAnalysisId,
                request
        ));
    }
}
