package com.pisey.elearning.dto.quiz;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Questions left out (or with no option ids) count as unanswered. */
public record AttemptSubmitRequest(
        @NotNull
        List<@Valid @NotNull AnswerRequest> answers
) {

    public record AnswerRequest(
            @NotNull
            Long questionId,
            List<@NotNull Long> optionIds
    ) {
    }
}
