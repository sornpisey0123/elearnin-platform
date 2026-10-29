package com.pisey.elearning.dto.quiz;

import com.pisey.elearning.entity.enums.QuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * SINGLE_CHOICE needs exactly one correct option, MULTIPLE_CHOICE at least one, TRUE_FALSE exactly two options with
 * one correct. The per-type rules are checked in the service.
 */
public record QuizQuestionRequest(
        @NotBlank
        @Size(max = 5000)
        String questionText,
        @NotNull
        QuestionType type,
        /** Defaults to 1. */
        @Min(0)
        Integer points,
        @NotNull
        @Size(min = 2)
        List<@Valid @NotNull QuizOptionRequest> options
) {
}
