package com.pisey.elearning.controller;

import com.pisey.elearning.dto.report.AdminOverviewResponse;
import com.pisey.elearning.dto.report.TopCourseResponse;
import com.pisey.elearning.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: reports")
public class AdminReportController {

    private final ReportService reportService;

    @GetMapping("/overview")
    @Operation(summary = "Users by role, courses by status, total enrollments and revenue")
    public AdminOverviewResponse overview() {
        return reportService.overview();
    }

    @GetMapping("/top-courses")
    @Operation(summary = "Top published/archived courses by enrollments or by rating (≥3 reviews)")
    public List<TopCourseResponse> topCourses(
            @Parameter(description = "enrollments (default) or rating")
            @RequestParam(required = false) String by,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return reportService.topCourses(by, limit);
    }
}
