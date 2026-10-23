package com.pisey.elearning.dto.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The section is appended after the last one. */
public record SectionCreateRequest(

        @NotBlank
        @Size(max = 200)
        String title
) {
}
