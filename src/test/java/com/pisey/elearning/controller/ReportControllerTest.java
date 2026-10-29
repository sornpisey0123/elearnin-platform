package com.pisey.elearning.controller;

import com.pisey.elearning.config.SecurityConfig;
import com.pisey.elearning.dto.report.AdminOverviewResponse;
import com.pisey.elearning.dto.report.EnrollmentTrendResponse;
import com.pisey.elearning.dto.report.InstructorCoursesReportResponse;
import com.pisey.elearning.dto.report.TopCourseResponse;
import com.pisey.elearning.entity.enums.CourseStatus;
import com.pisey.elearning.entity.enums.Role;
import com.pisey.elearning.exception.BadRequestException;
import com.pisey.elearning.exception.ForbiddenException;
import com.pisey.elearning.exception.ResourceNotFoundException;
import com.pisey.elearning.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AdminReportController.class, InstructorReportController.class})
@Import(SecurityConfig.class)
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ReportService reportService;

    // --- admin ---

    @Test
    void overviewForAdmin() throws Exception {
        Map<Role, Long> users = new LinkedHashMap<>(Map.of(Role.ADMIN, 1L, Role.INSTRUCTOR, 0L, Role.STUDENT, 4L));
        given(reportService.overview()).willReturn(new AdminOverviewResponse(users,
                new AdminOverviewResponse.CourseCounts(3, 2, 1, 0), 7, new BigDecimal("120.50")));

        mockMvc.perform(get("/api/admin/reports/overview").with(role("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usersByRole.INSTRUCTOR").value(0))
                .andExpect(jsonPath("$.courses.published").value(2))
                .andExpect(jsonPath("$.totalRevenue").value(120.50));
    }

    @Test
    void adminReportsAreAdminOnly() throws Exception {
        mockMvc.perform(get("/api/admin/reports/overview").with(role("INSTRUCTOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/reports/top-courses").with(role("STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/reports/overview"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(reportService);
    }

    @Test
    void topCoursesDefaultsToTenByEnrollments() throws Exception {
        given(reportService.topCourses(isNull(), eq(10))).willReturn(List.of(new TopCourseResponse(1L, "Java",
                "java", CourseStatus.PUBLISHED, "Dara", 12, new BigDecimal("4.50"), 4)));

        mockMvc.perform(get("/api/admin/reports/top-courses").with(role("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].enrollmentCount").value(12));
        verify(reportService).topCourses(null, 10);
    }

    @Test
    void topCoursesLimitMustBeOneToFifty() throws Exception {
        mockMvc.perform(get("/api/admin/reports/top-courses?limit=51").with(role("ADMIN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/reports/top-courses?limit=0").with(role("ADMIN")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reportService);
    }

    @Test
    void unknownRankingIs400() throws Exception {
        given(reportService.topCourses(eq("price"), anyInt()))
                .willThrow(new BadRequestException("Unknown ranking: price; use enrollments or rating"));

        mockMvc.perform(get("/api/admin/reports/top-courses?by=price").with(role("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown ranking: price; use enrollments or rating"));
    }

    // --- instructor ---

    @Test
    void instructorCoursesReport() throws Exception {
        given(reportService.instructorCourses(any(Jwt.class))).willReturn(new InstructorCoursesReportResponse(
                List.of(new InstructorCoursesReportResponse.CourseStats(1L, "Java", CourseStatus.PUBLISHED, 2, 1,
                        new BigDecimal("35.00"), new BigDecimal("4.50"), 2)),
                new InstructorCoursesReportResponse.Totals(1, 2, 1, new BigDecimal("35.00"),
                        new BigDecimal("4.50"), 2)));

        mockMvc.perform(get("/api/instructor/reports/courses").with(role("INSTRUCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses[0].completedEnrollments").value(1))
                .andExpect(jsonPath("$.totals.revenue").value(35.00));
    }

    @Test
    void instructorReportsNeedInstructorOrAdmin() throws Exception {
        mockMvc.perform(get("/api/instructor/reports/courses").with(role("STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/instructor/reports/courses/1/trend"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(reportService);
    }

    @Test
    void trendDefaultsToTwelveMonths() throws Exception {
        given(reportService.enrollmentTrend(eq(1L), eq(12), any(Jwt.class))).willReturn(new EnrollmentTrendResponse(
                1L, 12, List.of(new EnrollmentTrendResponse.MonthPoint("2026-10", 0, new BigDecimal("0.00")))));

        mockMvc.perform(get("/api/instructor/reports/courses/1/trend").with(role("INSTRUCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points[0].month").value("2026-10"))
                .andExpect(jsonPath("$.points[0].enrollments").value(0));
    }

    @Test
    void trendMonthsMustBeOneToTwentyFour() throws Exception {
        mockMvc.perform(get("/api/instructor/reports/courses/1/trend?months=25").with(role("INSTRUCTOR")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/instructor/reports/courses/1/trend?months=0").with(role("INSTRUCTOR")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reportService);
    }

    @Test
    void trendOfOthersCourseIs403AndUnknownIs404() throws Exception {
        given(reportService.enrollmentTrend(eq(1L), anyInt(), any(Jwt.class)))
                .willThrow(new ForbiddenException("You can only manage your own courses"));
        given(reportService.enrollmentTrend(eq(99L), anyInt(), any(Jwt.class)))
                .willThrow(new ResourceNotFoundException("Course", "id", 99L));

        mockMvc.perform(get("/api/instructor/reports/courses/1/trend").with(role("INSTRUCTOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/instructor/reports/courses/99/trend").with(role("INSTRUCTOR")))
                .andExpect(status().isNotFound());
    }

    private static RequestPostProcessor role(String role) {
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
