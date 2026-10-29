package com.pisey.elearning.dto.quiz;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QuizOptionRequest(
        @NotBlank
        @Size(max = 500)
        String optionText,
        /** Defaults to false. */
        Boolean correct
) {
}
