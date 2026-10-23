package com.pisey.elearning.dto.enrollment;

import com.pisey.elearning.dto.course.CourseSummaryResponse;
import com.pisey.elearning.entity.enums.EnrollmentStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** One of the caller's enrollments with its course card. */
public record MyCourseResponse(
        Long enrollmentId,
        EnrollmentStatus status,
        BigDecimal progressPct,
        Instant enrolledAt,
        Instant completedAt,
        CourseSummaryResponse course
) {
}
