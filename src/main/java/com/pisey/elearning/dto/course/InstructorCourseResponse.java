package com.pisey.elearning.dto.course;

import com.pisey.elearning.entity.enums.CourseLevel;
import com.pisey.elearning.entity.enums.CourseStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** Row in the instructor's own course list (all statuses). */
public record InstructorCourseResponse(
        Long id,
        String title,
        String slug,
        String thumbnailUrl,
        CourseStatus status,
        CourseLevel level,
        BigDecimal price,
        BigDecimal avgRating,
        int reviewCount,
        int enrollmentCount,
        String categoryName,
        Instant publishedAt,
        Instant updatedAt
) {
}
