package com.pisey.elearning.dto.content;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Every section id of the course exactly once, in the new order. */
public record SectionOrderRequest(

        @NotEmpty
        List<@NotNull Long> sectionIds
) {
}
