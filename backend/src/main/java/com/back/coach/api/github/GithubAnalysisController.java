package com.back.coach.api.github;

import com.back.coach.api.github.dto.GithubAnalysisRequest;
import com.back.coach.api.github.dto.GithubAnalysisResponse;
import com.back.coach.domain.github.entity.GithubAnalysis;
import com.back.coach.domain.github.repository.GithubAnalysisRepository;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.back.coach.global.response.ApiResponse;
import com.back.coach.global.security.AuthenticatedUser;
import com.back.coach.service.github.AnalysisPayloadJson;
import com.back.coach.service.github.GithubAnalysisService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/github-analyses")
public class GithubAnalysisController {

    private final GithubAnalysisService analysisService;
    private final GithubAnalysisRepository analysisRepository;
    private final AnalysisPayloadJson payloadJson;

    public GithubAnalysisController(GithubAnalysisService analysisService,
                                    GithubAnalysisRepository analysisRepository,
                                    AnalysisPayloadJson payloadJson) {
        this.analysisService = analysisService;
        this.analysisRepository = analysisRepository;
        this.payloadJson = payloadJson;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<GithubAnalysisResponse>> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody GithubAnalysisRequest request
    ) {
        GithubAnalysisService.GithubAnalysisResult result = analysisService.run(
                principal.userId(),
                request.githubConnectionId(),
                request.selectedRepositoryIds(),
                request.coreRepositoryIds()
        );
        return ResponseEntity.ok(ApiResponse.success(GithubAnalysisResponse.from(result)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GithubAnalysisResponse>> get(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id
    ) {
        GithubAnalysis entity = analysisRepository.findByIdAndUserId(id, principal.userId())
                .orElseThrow(() -> new ServiceException(ErrorCode.RESOURCE_NOT_FOUND));
        return ResponseEntity.ok(ApiResponse.success(
                GithubAnalysisResponse.from(entity, payloadJson.fromJson(entity.getAnalysisPayload()))
        ));
    }
}
