package com.back.coach.domain.diagnosis.controller;

import com.back.coach.domain.diagnosis.dto.DiagnosisDetailResponse;
import com.back.coach.domain.diagnosis.service.DiagnosisDetailService;
import com.back.coach.global.response.ApiResponse;
import com.back.coach.global.security.AuthenticatedUser;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/diagnoses", produces = MediaType.APPLICATION_JSON_VALUE)
public class DiagnosisDetailController {

    private final DiagnosisDetailService diagnosisDetailService;

    public DiagnosisDetailController(DiagnosisDetailService diagnosisDetailService) {
        this.diagnosisDetailService = diagnosisDetailService;
    }

    @GetMapping("/{diagnosisId}")
    public ApiResponse<DiagnosisDetailResponse> findDiagnosis(
            Authentication authentication,
            @PathVariable Long diagnosisId
    ) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();

        return ApiResponse.success(diagnosisDetailService.findDiagnosis(authenticatedUser.userId(), diagnosisId));
    }
}
