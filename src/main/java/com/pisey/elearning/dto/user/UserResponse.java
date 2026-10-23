package com.pisey.elearning.dto.user;

import com.pisey.elearning.entity.enums.Role;

import java.time.Instant;
import java.util.UUID;

/** Admin view of a user. */
public record UserResponse(
        Long id,
        UUID keycloakId,
        String fullName,
        String email,
        Role role,
        boolean active,
        String avatarUrl,
        Instant createdAt,
        Instant updatedAt
) {
}
