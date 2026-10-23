package com.pisey.elearning.dto.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Renames a section; position changes go through the reorder endpoint. */
public record SectionUpdateRequest(

        @NotBlank
        @Size(max = 200)
        String title
) {
}
