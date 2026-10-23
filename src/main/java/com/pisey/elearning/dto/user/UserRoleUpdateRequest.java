package com.pisey.elearning.dto.user;

import com.pisey.elearning.entity.enums.Role;
import jakarta.validation.constraints.NotNull;

public record UserRoleUpdateRequest(

        @NotNull
        Role role
) {
}
