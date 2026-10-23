package com.pisey.elearning.dto.category;

/** {@code courseCount} counts PUBLISHED courses only. */
public record CategoryResponse(
        Long id,
        String name,
        String slug,
        String description,
        long courseCount
) {
}
