package com.pisey.elearning.dto.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Full replacement of rating and comment; a null comment removes it. */
public record ReviewUpdateRequest(
        @NotNull
        @Min(1)
        @Max(5)
        Integer rating,
        @Size(max = 2000)
        String comment
) {
}
