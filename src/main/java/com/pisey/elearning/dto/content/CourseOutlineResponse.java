package com.pisey.elearning.dto.content;

import com.pisey.elearning.entity.enums.LessonType;

import java.util.List;

/** Public table of contents. Never contains lesson content (videoUrl, body, fileUrl). */
public record CourseOutlineResponse(
        Long courseId,
        String title,
        List<SectionOutline> sections
) {

    public record SectionOutline(Long id, String title, Integer sortOrder, List<LessonOutline> lessons) {
    }

    public record LessonOutline(Long id, String title, LessonType type, Integer durationSeconds, boolean preview,
                                boolean hasQuiz) {
    }
}
