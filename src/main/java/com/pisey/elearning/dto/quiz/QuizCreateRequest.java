package com.pisey.elearning.dto.quiz;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Questions keep the array order. At least one question must be worth points. */
public record QuizCreateRequest(
        @NotBlank
        @Size(max = 200)
        String title,
        @NotNull
        @Min(0)
        @Max(100)
        Integer passMark,
        /** Null means no time limit. */
        @Min(1)
        Integer timeLimitMinutes,
        @NotEmpty
        List<@Valid @NotNull QuizQuestionRequest> questions
) {
}
