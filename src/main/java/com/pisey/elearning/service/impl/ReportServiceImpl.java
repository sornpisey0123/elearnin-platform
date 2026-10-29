package com.pisey.elearning.service.impl;

import com.pisey.elearning.dto.report.AdminOverviewResponse;
import com.pisey.elearning.dto.report.AdminOverviewResponse.CourseCounts;
import com.pisey.elearning.dto.report.EnrollmentTrendResponse;
import com.pisey.elearning.dto.report.EnrollmentTrendResponse.MonthPoint;
import com.pisey.elearning.dto.report.InstructorCoursesReportResponse;
import com.pisey.elearning.dto.report.InstructorCoursesReportResponse.CourseStats;
import com.pisey.elearning.dto.report.InstructorCoursesReportResponse.Totals;
import com.pisey.elearning.dto.report.TopCourseResponse;
import com.pisey.elearning.entity.Course;
import com.pisey.elearning.entity.enums.CourseStatus;
import com.pisey.elearning.entity.enums.EnrollmentStatus;
import com.pisey.elearning.entity.enums.Role;
import com.pisey.elearning.exception.BadRequestException;
import com.pisey.elearning.exception.ResourceNotFoundException;
import com.pisey.elearning.repository.CourseRepository;
import com.pisey.elearning.repository.EnrollmentRepository;
import com.pisey.elearning.repository.ReviewRepository;
import com.pisey.elearning.repository.UserRepository;
import com.pisey.elearning.repository.projection.EnrollmentTotals;
import com.pisey.elearning.repository.projection.RoleCount;
import com.pisey.elearning.repository.projection.StatusCount;
import com.pisey.elearning.repository.projection.TopCourseView;
import com.pisey.elearning.service.CourseAccessService;
import com.pisey.elearning.service.ReportService;
import com.pisey.elearning.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

    /** Drafts never have enrollments or reviews, so they would only pad the ranking. */
    private static final List<CourseStatus> RANKED_STATUSES = List.of(CourseStatus.PUBLISHED, CourseStatus.ARCHIVED);

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ReviewRepository reviewRepository;
    private final CourseAccessService courseAccessService;

    @Override
    public AdminOverviewResponse overview() {
        Map<Role, Long> usersByRole = new EnumMap<>(Role.class);
        for (Role role : Role.values()) {
            usersByRole.put(role, 0L);
        }
        userRepository.countGroupedByRole().forEach(row -> usersByRole.put(row.getRole(), row.getCount()));

        Map<CourseStatus, Long> coursesByStatus = courseRepository.countGroupedByStatus().stream()
                .collect(Collectors.toMap(StatusCount::getStatus, StatusCount::getCount));
        CourseCounts courses = new CourseCounts(
                coursesByStatus.values().stream().mapToLong(Long::longValue).sum(),
                coursesByStatus.getOrDefault(CourseStatus.PUBLISHED, 0L),
                coursesByStatus.getOrDefault(CourseStatus.DRAFT, 0L),
                coursesByStatus.getOrDefault(CourseStatus.ARCHIVED, 0L));

        EnrollmentTotals enrollments = enrollmentRepository.findTotals();
        return new AdminOverviewResponse(usersByRole, courses, enrollments.getEnrollments(),
                money(enrollments.getRevenue()));
    }

    @Override
    public List<TopCourseResponse> topCourses(String by, int limit) {
        PageRequest page = PageRequest.of(0, limit);
        List<TopCourseView> rows = switch (by == null ? "" : by.trim().toLowerCase(Locale.ROOT)) {
            case "", "enrollments" -> courseRepository.findTopByEnrollments(RANKED_STATUSES, page);
            case "rating" -> courseRepository.findTopByRating(RANKED_STATUSES, MIN_REVIEWS_FOR_RATING, page);
            default -> throw new BadRequestException("Unknown ranking: " + by + "; use enrollments or rating");
        };
        return rows.stream()
                .map(row -> new TopCourseResponse(row.getId(), row.getTitle(), row.getSlug(), row.getStatus(),
                        row.getInstructorName(), row.getEnrollmentCount(), row.getAvgRating(), row.getReviewCount()))
                .toList();
    }

    @Override
    public InstructorCoursesReportResponse instructorCourses(Jwt caller) {
        UUID instructor = SecurityUtils.keycloakId(caller);
        List<CourseStats> courses = courseRepository.findInstructorCourseStats(instructor, EnrollmentStatus.COMPLETED)
                .stream()
                .map(row -> new CourseStats(row.getCourseId(), row.getTitle(), row.getStatus(), row.getEnrollments(),
                        row.getCompletedEnrollments(), money(row.getRevenue()), row.getAvgRating(),
                        row.getReviewCount()))
                .toList();

        // Adding up the per-course rows (already aggregated in SQL); the rating is averaged over the reviews themselves
        Double averageRating = reviewRepository.findAverageRatingByInstructor(instructor);
        Totals totals = new Totals(
                courses.size(),
                courses.stream().mapToLong(CourseStats::enrollments).sum(),
                courses.stream().mapToLong(CourseStats::completedEnrollments).sum(),
                courses.stream().map(CourseStats::revenue).reduce(money(BigDecimal.ZERO), BigDecimal::add),
                averageRating == null ? money(BigDecimal.ZERO)
                        : BigDecimal.valueOf(averageRating).setScale(2, RoundingMode.HALF_UP),
                courses.stream().mapToLong(CourseStats::reviewCount).sum());
        return new InstructorCoursesReportResponse(courses, totals);
    }

    @Override
    public EnrollmentTrendResponse enrollmentTrend(Long courseId, int months, Jwt caller) {
        Course course = courseRepository.findWithInstructorAndCategoryById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", courseId));
        courseAccessService.requireOwnerOrAdmin(course, caller);

        List<MonthPoint> points = enrollmentRepository.findMonthlyTrend(courseId, months).stream()
                .map(row -> new MonthPoint(row.getMonth(), row.getEnrollments(), money(row.getRevenue())))
                .toList();
        return new EnrollmentTrendResponse(courseId, months, points);
    }

    /** Sums come back with the scale of the data (or none for 0); answers always have 2 decimals. */
    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
