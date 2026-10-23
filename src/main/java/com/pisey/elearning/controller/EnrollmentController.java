package com.pisey.elearning.controller;

import com.pisey.elearning.dto.common.PageResponse;
import com.pisey.elearning.dto.enrollment.ContinueLearningResponse;
import com.pisey.elearning.dto.enrollment.EnrollmentResponse;
import com.pisey.elearning.dto.enrollment.LessonProgressResponse;
import com.pisey.elearning.dto.enrollment.MyCourseResponse;
import com.pisey.elearning.service.EnrollmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Enrolling and the caller's own learning progress. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Enrollment & progress")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @PostMapping("/api/courses/{courseId}/enroll")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Enroll in a published course at its current price")
    public EnrollmentResponse enroll(@PathVariable Long courseId, @AuthenticationPrincipal Jwt jwt) {
        return enrollmentService.enroll(courseId, jwt);
    }

    @GetMapping("/api/me/courses")
    @Operation(summary = "My enrolled courses with progress (newest enrollment first)")
    public PageResponse<MyCourseResponse> myCourses(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "in-progress, completed, or omit for both")
            @RequestParam(required = false) String status,
            @SortDefault(sort = "enrolledAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return enrollmentService.findMyCourses(jwt, status, pageable);
    }

    @PostMapping("/api/me/lessons/{lessonId}/complete")
    @Operation(summary = "Mark a lesson done (idempotent)")
    public LessonProgressResponse complete(@PathVariable Long lessonId, @AuthenticationPrincipal Jwt jwt) {
        return enrollmentService.completeLesson(lessonId, jwt);
    }

    @DeleteMapping("/api/me/lessons/{lessonId}/complete")
    @Operation(summary = "Mark a lesson not done")
    public LessonProgressResponse uncomplete(@PathVariable Long lessonId, @AuthenticationPrincipal Jwt jwt) {
        return enrollmentService.uncompleteLesson(lessonId, jwt);
    }

    @GetMapping("/api/me/courses/{courseId}/continue")
    @Operation(summary = "The lesson to resume: first incomplete in outline order")
    public ContinueLearningResponse continueLearning(@PathVariable Long courseId, @AuthenticationPrincipal Jwt jwt) {
        return enrollmentService.continueLearning(courseId, jwt);
    }
}
