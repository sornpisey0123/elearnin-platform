package com.pisey.elearning.controller;

import com.pisey.elearning.dto.common.PageResponse;
import com.pisey.elearning.dto.user.UserResponse;
import com.pisey.elearning.dto.user.UserRoleUpdateRequest;
import com.pisey.elearning.dto.user.UserStatusUpdateRequest;
import com.pisey.elearning.entity.enums.Role;
import com.pisey.elearning.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.SortDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: users")
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "List users (paged; filter by role, search name or email)")
    public PageResponse<UserResponse> findAll(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) String keyword,
            @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return userService.findAll(role, keyword, pageable);
    }

    @PatchMapping("/{id}/role")
    @Operation(summary = "Change a user's role (Keycloak and local; not your own)")
    public UserResponse changeRole(@PathVariable Long id,
                                   @Valid @RequestBody UserRoleUpdateRequest request,
                                   @AuthenticationPrincipal Jwt jwt) {
        return userService.changeRole(id, request.role(), jwt);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Enable or disable a user (Keycloak and local; not yourself)")
    public UserResponse changeStatus(@PathVariable Long id,
                                     @Valid @RequestBody UserStatusUpdateRequest request,
                                     @AuthenticationPrincipal Jwt jwt) {
        return userService.changeStatus(id, request.active(), jwt);
    }
}
