package com.pisey.elearning.controller;

import com.pisey.elearning.entity.User;
import com.pisey.elearning.service.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TEMPORARY (Phase 1 check). Remove after Phase 9.
 */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
@Tag(name = "Debug")
public class DebugTokenController {

    private final CurrentUserService currentUserService;

    @GetMapping("/debug-token")
    @Operation(summary = "Show token claims, mapped authorities and the provisioned local user (temporary)")
    public Map<String, Object> debugToken(@AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        User user = currentUserService.getOrCreate(jwt);

        Map<String, Object> localUser = new LinkedHashMap<>();
        localUser.put("id", user.getId());
        localUser.put("keycloakId", user.getKeycloakId());
        localUser.put("email", user.getEmail());
        localUser.put("fullName", user.getFullName());
        localUser.put("role", user.getRole());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("authorities", authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
        body.put("localUser", localUser);
        body.put("claims", jwt.getClaims());
        return body;
    }
}
