package com.pisey.elearning.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code PUT /api/me/password}. Matching and "new differs from current" are checked in the service. */
public record PasswordChangeRequest(

        @NotBlank
        String currentPassword,

        @NotBlank
        @Size(min = 8, max = 128)
        String newPassword,

        @NotBlank
        String confirmPassword
) {
}
