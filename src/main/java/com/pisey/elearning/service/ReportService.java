package com.pisey.elearning.service;

import com.pisey.elearning.dto.report.AdminOverviewResponse;
import com.pisey.elearning.dto.report.EnrollmentTrendResponse;
import com.pisey.elearning.dto.report.InstructorCoursesReportResponse;
import com.pisey.elearning.dto.report.TopCourseResponse;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/** Read-only reports. Every number is aggregated in the database; no entities are loaded to count. */
public interface ReportService {

    int MIN_REVIEWS_FOR_RATING = 3;

    AdminOverviewResponse overview();

    /**
     * Published and archived courses ranked by {@code by}: {@code enrollments} or {@code rating} (courses with at
     * least {@link #MIN_REVIEWS_FOR_RATING} reviews only). Unknown {@code by} is 400.
     */
    List<TopCourseResponse> topCourses(String by, int limit);

    /** The caller's own courses. */
    InstructorCoursesReportResponse instructorCourses(Jwt caller);

    /** Monthly enrollments of a course the caller owns (or any course for ADMIN). */
    EnrollmentTrendResponse enrollmentTrend(Long courseId, int months, Jwt caller);
}
