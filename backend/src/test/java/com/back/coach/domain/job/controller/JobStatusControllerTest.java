package com.back.coach.domain.job.controller;

import com.back.coach.domain.job.service.JobStatusService;
import com.back.coach.domain.job.service.JobStatusSnapshot;
import com.back.coach.global.code.JobStatus;
import com.back.coach.global.exception.GlobalExceptionHandler;
import com.back.coach.global.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class JobStatusControllerTest {

    @Mock
    private JobStatusService jobStatusService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new JobStatusController(jobStatusService,
                        org.mockito.Mockito.mock(com.back.coach.domain.job.service.JobHistoryService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void findStatus_whenJobExists_returnsJobStatus() throws Exception {
        given(jobStatusService.find(1L, "job-1"))
                .willReturn(Optional.of(new JobStatusSnapshot("job-1", JobStatus.RUNNING, "FETCH_REPOSITORIES", null, null)));

        mockMvc.perform(get("/api/jobs/{jobId}/status", "job-1")
                        .principal(authentication(1L))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").value("job-1"))
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.currentStep").value("FETCH_REPOSITORIES"))
                .andExpect(jsonPath("$.data.error").value(nullValue()))
                .andExpect(jsonPath("$.meta").isMap());

        verify(jobStatusService).find(1L, "job-1");
    }

    @Test
    void findStatus_whenJobFailed_returnsError() throws Exception {
        given(jobStatusService.find(1L, "job-failed"))
                .willReturn(Optional.of(new JobStatusSnapshot("job-failed", JobStatus.FAILED, "LLM_SUMMARY", "LLM timeout", null)));

        mockMvc.perform(get("/api/jobs/{jobId}/status", "job-failed")
                        .principal(authentication(1L))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").value("job-failed"))
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.currentStep").value("LLM_SUMMARY"))
                .andExpect(jsonPath("$.data.error").value("LLM timeout"))
                .andExpect(jsonPath("$.meta").isMap());

        verify(jobStatusService).find(1L, "job-failed");
    }

    @Test
    void findStatus_whenJobDoesNotExist_returnsNotFound() throws Exception {
        given(jobStatusService.find(1L, "missing-job")).willReturn(Optional.empty());

        mockMvc.perform(get("/api/jobs/{jobId}/status", "missing-job")
                        .principal(authentication(1L))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("작업 상태를 찾을 수 없습니다."));

        verify(jobStatusService).find(1L, "missing-job");
    }

    private Authentication authentication(Long userId) {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(userId), null);
    }
}
