package com.pisey.elearning.dto.enrollment;

import com.pisey.elearning.entity.enums.EnrollmentStatus;

import java.math.BigDecimal;

/** Result of completing or un-completing a lesson: the lesson's state and the course progress after it. */
public record LessonProgressResponse(
        Long lessonId,
        boolean lessonCompleted,
        Long courseId,
        EnrollmentStatus enrollmentStatus,
        BigDecimal progressPct,
        long completedLessons,
        long totalLessons
) {
}
