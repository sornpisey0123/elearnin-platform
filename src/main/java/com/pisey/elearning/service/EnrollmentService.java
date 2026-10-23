package com.pisey.elearning.service;

import com.pisey.elearning.dto.common.PageResponse;
import com.pisey.elearning.dto.enrollment.ContinueLearningResponse;
import com.pisey.elearning.dto.enrollment.EnrollmentResponse;
import com.pisey.elearning.dto.enrollment.LessonProgressResponse;
import com.pisey.elearning.dto.enrollment.MyCourseResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;

public interface EnrollmentService {

    /**
     * Enrolls the caller at the current price. 400 unless the course is PUBLISHED, 400 for the instructor's own
     * course, 409 if already enrolled.
     */
    EnrollmentResponse enroll(Long courseId, Jwt caller);

    /** {@code status}: "in-progress" (ACTIVE), "completed" (COMPLETED) or null for both. */
    PageResponse<MyCourseResponse> findMyCourses(Jwt caller, String status, Pageable pageable);

    /** Idempotent; recalculates progress and completes the enrollment at 100%. */
    LessonProgressResponse completeLesson(Long lessonId, Jwt caller);

    /** Recalculates progress; a COMPLETED enrollment goes back to ACTIVE. */
    LessonProgressResponse uncompleteLesson(Long lessonId, Jwt caller);

    ContinueLearningResponse continueLearning(Long courseId, Jwt caller);
}
