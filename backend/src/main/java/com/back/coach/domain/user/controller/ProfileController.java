package com.back.coach.domain.user.controller;

import com.back.coach.domain.user.dto.ProfileDetailResponse;
import com.back.coach.domain.user.dto.ProfileSaveRequest;
import com.back.coach.domain.user.dto.ProfileSaveResponse;
import com.back.coach.domain.user.dto.ProfileSaveResult;
import com.back.coach.domain.user.service.ProfileService;
import com.back.coach.global.response.ApiResponse;
import com.back.coach.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/profiles", produces = MediaType.APPLICATION_JSON_VALUE)
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<ProfileSaveResponse> saveProfile(
            Authentication authentication,
            @Valid @RequestBody ProfileSaveRequest request
    ) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        ProfileSaveResult result = profileService.saveProfile(authenticatedUser.userId(), request);

        return ApiResponse.success(ProfileSaveResponse.from(result));
    }

    @GetMapping("/me")
    public ApiResponse<ProfileDetailResponse> findMyProfile(Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();

        return ApiResponse.success(profileService.findMyProfile(authenticatedUser.userId()));
    }
}
