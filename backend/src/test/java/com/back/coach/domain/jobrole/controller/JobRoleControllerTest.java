package com.back.coach.domain.jobrole.controller;

import com.back.coach.domain.jobrole.dto.JobRoleResponse;
import com.back.coach.domain.jobrole.service.JobRoleService;
import com.back.coach.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class JobRoleControllerTest {

    @Mock
    private JobRoleService jobRoleService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new JobRoleController(jobRoleService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listJobRoles_returnsActiveRoles() throws Exception {
        given(jobRoleService.listActive()).willReturn(List.of(
                new JobRoleResponse("BACKEND_DEVELOPER", "백엔드 개발자", "서버 사이드 개발"),
                new JobRoleResponse("FRONTEND_DEVELOPER", "프론트엔드 개발자", "웹 UI 개발")
        ));

        mockMvc.perform(get("/api/job-roles").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].roleCode").value("BACKEND_DEVELOPER"))
                .andExpect(jsonPath("$.data[0].roleName").value("백엔드 개발자"))
                .andExpect(jsonPath("$.data[0].description").value("서버 사이드 개발"))
                .andExpect(jsonPath("$.data[1].roleCode").value("FRONTEND_DEVELOPER"))
                .andExpect(jsonPath("$.meta").isMap());
    }

    @Test
    void listJobRoles_whenEmpty_returnsEmptyArray() throws Exception {
        given(jobRoleService.listActive()).willReturn(List.of());

        mockMvc.perform(get("/api/job-roles").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
