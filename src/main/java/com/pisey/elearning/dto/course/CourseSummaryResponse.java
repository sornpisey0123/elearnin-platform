package com.pisey.elearning.dto.course;

import com.pisey.elearning.entity.enums.CourseLevel;

import java.math.BigDecimal;

/** Catalogue card for a course. */
public record CourseSummaryResponse(
        Long id,
        String title,
        String slug,
        String thumbnailUrl,
        CourseLevel level,
        BigDecimal price,
        BigDecimal avgRating,
        int reviewCount,
        int enrollmentCount,
        String instructorName,
        String categoryName
) {
}
