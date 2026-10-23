package com.pisey.elearning.controller;

import com.pisey.elearning.dto.user.PasswordChangeRequest;
import com.pisey.elearning.dto.user.ProfileResponse;
import com.pisey.elearning.dto.user.ProfileUpdateRequest;
import com.pisey.elearning.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
@Tag(name = "My account")
public class MeController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Get my profile (creates the local user on first call)")
    public ProfileResponse getProfile(@AuthenticationPrincipal Jwt jwt) {
        return userService.getProfile(jwt);
    }

    @PutMapping
    @Operation(summary = "Update my name, bio and optionally email (synced to Keycloak)")
    public ProfileResponse updateProfile(@AuthenticationPrincipal Jwt jwt,
                                         @Valid @RequestBody ProfileUpdateRequest request) {
        return userService.updateProfile(jwt, request);
    }

    @PostMapping(path = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload my avatar (jpg, png or webp, max 2 MB)")
    public ProfileResponse uploadAvatar(@AuthenticationPrincipal Jwt jwt,
                                        @RequestPart("file") MultipartFile file) {
        return userService.updateAvatar(jwt, file);
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Change my password (current password required)")
    public void changePassword(@AuthenticationPrincipal Jwt jwt,
                               @Valid @RequestBody PasswordChangeRequest request) {
        userService.changePassword(jwt, request);
    }
}
