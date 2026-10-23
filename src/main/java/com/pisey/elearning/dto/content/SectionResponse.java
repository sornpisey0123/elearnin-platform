package com.pisey.elearning.dto.content;

public record SectionResponse(
        Long id,
        Long courseId,
        String title,
        Integer sortOrder
) {
}
