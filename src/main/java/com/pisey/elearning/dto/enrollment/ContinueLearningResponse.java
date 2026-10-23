package com.pisey.elearning.dto.enrollment;

import com.pisey.elearning.entity.enums.LessonType;

/**
 * Where to resume: the first incomplete lesson in outline order, or — when {@code courseCompleted} — the first
 * lesson. {@code lesson} is null only for a course without lessons.
 */
public record ContinueLearningResponse(
        Long courseId,
        boolean courseCompleted,
        LessonRef lesson
) {

    public record LessonRef(Long id, String title, LessonType type, Long sectionId, String sectionTitle) {
    }
}
