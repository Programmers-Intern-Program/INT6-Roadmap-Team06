package com.back.coach.domain.job.controller;

import com.back.coach.domain.job.dto.JobHistoryResponse;
import com.back.coach.domain.job.dto.JobStatusResponse;
import com.back.coach.domain.job.service.JobHistoryService;
import com.back.coach.domain.job.service.JobStatusService;
import com.back.coach.domain.job.service.JobStatusSnapshot;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.back.coach.global.response.ApiResponse;
import com.back.coach.global.security.AuthenticatedUser;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(path = "/api/jobs", produces = MediaType.APPLICATION_JSON_VALUE)
public class JobStatusController {

    private static final int DEFAULT_HISTORY_LIMIT = 20;
    private static final int MAX_HISTORY_LIMIT = 50;

    private final JobStatusService jobStatusService;
    private final JobHistoryService jobHistoryService;

    public JobStatusController(
            JobStatusService jobStatusService,
            JobHistoryService jobHistoryService
    ) {
        this.jobStatusService = jobStatusService;
        this.jobHistoryService = jobHistoryService;
    }

    @GetMapping("/history")
    public ApiResponse<List<JobHistoryResponse>> listHistory(
            Authentication authentication,
            @RequestParam(defaultValue = "20") int limit
    ) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        int effectiveLimit = Math.max(1, Math.min(limit < 1 ? DEFAULT_HISTORY_LIMIT : limit, MAX_HISTORY_LIMIT));
        return ApiResponse.success(
                jobHistoryService.findRecent(authenticatedUser.userId(), effectiveLimit).stream()
                        .map(JobHistoryResponse::from)
                        .toList()
        );
    }

    @GetMapping("/{jobId}/status")
    public ApiResponse<JobStatusResponse> findStatus(
            Authentication authentication,
            @PathVariable String jobId
    ) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        JobStatusSnapshot snapshot = jobStatusService.find(authenticatedUser.userId(), jobId)
                .orElseThrow(() -> new ServiceException(ErrorCode.RESOURCE_NOT_FOUND, "작업 상태를 찾을 수 없습니다."));

        return ApiResponse.success(JobStatusResponse.from(snapshot));
    }
}
