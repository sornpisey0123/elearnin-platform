package com.pisey.elearning.dto.report;

import com.pisey.elearning.entity.enums.CourseStatus;

import java.math.BigDecimal;

public record TopCourseResponse(
        Long id,
        String title,
        String slug,
        CourseStatus status,
        String instructorName,
        int enrollmentCount,
        BigDecimal avgRating,
        int reviewCount
) {
}
