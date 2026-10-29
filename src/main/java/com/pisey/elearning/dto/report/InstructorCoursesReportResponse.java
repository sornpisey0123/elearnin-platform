package com.pisey.elearning.dto.report;

import com.pisey.elearning.entity.enums.CourseStatus;

import java.math.BigDecimal;
import java.util.List;

/**
 * The instructor's courses (every status) with enrollments and revenue. {@code totals.avgRating} averages every
 * review across the courses, so courses with more reviews weigh more.
 */
public record InstructorCoursesReportResponse(
        List<CourseStats> courses,
        Totals totals
) {

    public record CourseStats(Long courseId, String title, CourseStatus status, long enrollments,
                              long completedEnrollments, BigDecimal revenue, BigDecimal avgRating, int reviewCount) {
    }

    public record Totals(int courses, long enrollments, long completedEnrollments, BigDecimal revenue,
                         BigDecimal avgRating, long reviewCount) {
    }
}
