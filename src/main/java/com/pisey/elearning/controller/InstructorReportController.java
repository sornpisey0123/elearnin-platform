package com.pisey.elearning.controller;

import com.pisey.elearning.dto.report.EnrollmentTrendResponse;
import com.pisey.elearning.dto.report.InstructorCoursesReportResponse;
import com.pisey.elearning.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reports on the caller's own courses (an ADMIN may read any course's trend). */
@RestController
@RequestMapping("/api/instructor/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
@Tag(name = "Instructor: reports")
public class InstructorReportController {

    private final ReportService reportService;

    @GetMapping("/courses")
    @Operation(summary = "My courses with enrollments, completions, revenue and rating, plus totals")
    public InstructorCoursesReportResponse courses(@AuthenticationPrincipal Jwt jwt) {
        return reportService.instructorCourses(jwt);
    }

    @GetMapping("/courses/{courseId}/trend")
    @Operation(summary = "Enrollments per month (UTC) for the last N months, oldest first, empty months as 0")
    public EnrollmentTrendResponse trend(@PathVariable Long courseId,
                                         @RequestParam(defaultValue = "12") @Min(1) @Max(24) int months,
                                         @AuthenticationPrincipal Jwt jwt) {
        return reportService.enrollmentTrend(courseId, months, jwt);
    }
}
