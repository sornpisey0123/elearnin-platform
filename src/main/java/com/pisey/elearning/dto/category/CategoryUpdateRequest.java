package com.pisey.elearning.dto.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Renaming regenerates the slug. */
public record CategoryUpdateRequest(

        @NotBlank
        @Size(max = 100)
        String name,

        @Size(max = 2000)
        String description
) {
}
