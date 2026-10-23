package com.pisey.elearning.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code PUT /api/me}. {@code email} is optional; when null or blank the current email is kept.
 */
public record ProfileUpdateRequest(

        @NotBlank
        @Size(max = 120)
        String fullName,

        @Size(max = 5000)
        String bio,

        @Email
        @Size(max = 255)
        String email
) {
}
