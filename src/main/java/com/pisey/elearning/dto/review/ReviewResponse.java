package com.pisey.elearning.dto.review;

import java.time.Instant;

public record ReviewResponse(
        Long id,
        Long courseId,
        Integer rating,
        String comment,
        Instant createdAt,
        Instant updatedAt,
        Author author
) {

    public record Author(Long id, String fullName, String avatarUrl) {
    }
}
