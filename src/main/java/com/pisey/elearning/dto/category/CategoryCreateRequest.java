package com.pisey.elearning.dto.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The slug is generated from the name. */
public record CategoryCreateRequest(

        @NotBlank
        @Size(max = 100)
        String name,

        @Size(max = 2000)
        String description
) {
}
