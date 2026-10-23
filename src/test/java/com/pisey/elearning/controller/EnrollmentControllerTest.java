package com.pisey.elearning.controller;

import com.pisey.elearning.config.SecurityConfig;
import com.pisey.elearning.dto.common.PageResponse;
import com.pisey.elearning.dto.enrollment.ContinueLearningResponse;
import com.pisey.elearning.dto.enrollment.EnrollmentResponse;
import com.pisey.elearning.dto.enrollment.LessonProgressResponse;
import com.pisey.elearning.entity.enums.EnrollmentStatus;
import com.pisey.elearning.entity.enums.LessonType;
import com.pisey.elearning.exception.BadRequestException;
import com.pisey.elearning.exception.ConflictException;
import com.pisey.elearning.exception.ForbiddenException;
import com.pisey.elearning.exception.ResourceNotFoundException;
import com.pisey.elearning.service.EnrollmentService;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EnrollmentController.class)
@Import(SecurityConfig.class)
class EnrollmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private EnrollmentService enrollmentService;

    @Test
    void enrollIs201() throws Exception {
        given(enrollmentService.enroll(eq(10L), any(Jwt.class))).willReturn(new EnrollmentResponse(40L, 10L,
                EnrollmentStatus.ACTIVE, new BigDecimal("19.99"), BigDecimal.ZERO, Instant.now(), null));

        mockMvc.perform(post("/api/courses/10/enroll").with(role("STUDENT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pricePaid").value(19.99))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void enrollTwiceIs409() throws Exception {
        given(enrollmentService.enroll(eq(10L), any(Jwt.class))).willThrow(new ConflictException("Already enrolled in this course"));

        mockMvc.perform(post("/api/courses/10/enroll").with(role("STUDENT")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Already enrolled in this course"));
    }

    @Test
    void enrollDraftOrOwnCourseIs400AndUnknownIs404() throws Exception {
        given(enrollmentService.enroll(eq(11L), any(Jwt.class))).willThrow(new BadRequestException("Only published courses can be enrolled in"));
        given(enrollmentService.enroll(eq(99L), any(Jwt.class))).willThrow(new ResourceNotFoundException("Course", "id", 99L));

        mockMvc.perform(post("/api/courses/11/enroll").with(role("STUDENT"))).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/courses/99/enroll").with(role("STUDENT"))).andExpect(status().isNotFound());
    }

    @Test
    void enrollNeedsStudentRoleAndToken() throws Exception {
        mockMvc.perform(post("/api/courses/10/enroll").with(role("INSTRUCTOR"))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/courses/10/enroll")).andExpect(status().isUnauthorized());
        verifyNoInteractions(enrollmentService);
    }

    @Test
    void myCoursesPassesStatusFilter() throws Exception {
        given(enrollmentService.findMyCourses(any(Jwt.class), eq("completed"), any()))
                .willReturn(new PageResponse<>(List.of(), 0, 12, 0, 0));

        mockMvc.perform(get("/api/me/courses").param("status", "completed").with(role("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void completeAndUncomplete() throws Exception {
        given(enrollmentService.completeLesson(eq(30L), any(Jwt.class))).willReturn(new LessonProgressResponse(
                30L, true, 10L, EnrollmentStatus.COMPLETED, new BigDecimal("100.00"), 3, 3));
        given(enrollmentService.uncompleteLesson(eq(30L), any(Jwt.class))).willReturn(new LessonProgressResponse(
                30L, false, 10L, EnrollmentStatus.ACTIVE, new BigDecimal("66.67"), 2, 3));

        mockMvc.perform(post("/api/me/lessons/30/complete").with(role("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrollmentStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.progressPct").value(100.00));
        mockMvc.perform(delete("/api/me/lessons/30/complete").with(role("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrollmentStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.progressPct").value(66.67));
    }

    @Test
    void completeNotEnrolledIs403() throws Exception {
        given(enrollmentService.completeLesson(eq(30L), any(Jwt.class)))
                .willThrow(new ForbiddenException("You are not enrolled in this course"));

        mockMvc.perform(post("/api/me/lessons/30/complete").with(role("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void continueLearning() throws Exception {
        given(enrollmentService.continueLearning(eq(10L), any(Jwt.class))).willReturn(new ContinueLearningResponse(
                10L, false, new ContinueLearningResponse.LessonRef(31L, "Setup", LessonType.VIDEO, 20L, "Intro")));

        mockMvc.perform(get("/api/me/courses/10/continue").with(role("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseCompleted").value(false))
                .andExpect(jsonPath("$.lesson.id").value(31));
    }

    @Test
    void meEndpointsNeedToken() throws Exception {
        mockMvc.perform(get("/api/me/courses")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/me/lessons/30/complete")).andExpect(status().isUnauthorized());
        verifyNoInteractions(enrollmentService);
    }

    private static RequestPostProcessor role(String role) {
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
