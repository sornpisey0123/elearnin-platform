package com.pisey.elearning.dto.content;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Every lesson id of the section exactly once, in the new order. */
public record LessonOrderRequest(

        @NotEmpty
        List<@NotNull Long> lessonIds
) {
}
