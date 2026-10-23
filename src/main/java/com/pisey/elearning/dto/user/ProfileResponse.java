package com.pisey.elearning.dto.user;

import com.pisey.elearning.entity.enums.Role;

/** The caller's own profile ({@code /api/me}). */
public record ProfileResponse(
        Long id,
        String fullName,
        String email,
        Role role,
        String avatarUrl,
        String bio
) {
}
