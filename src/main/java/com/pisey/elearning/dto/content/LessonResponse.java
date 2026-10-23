package com.pisey.elearning.dto.content;

import com.pisey.elearning.entity.enums.LessonType;

import java.util.List;

/** Full lesson content: for the owner, and for students allowed to access it. */
public record LessonResponse(
        Long id,
        Long courseId,
        Long sectionId,
        String title,
        LessonType type,
        String videoUrl,
        Integer durationSeconds,
        String body,
        String fileUrl,
        boolean preview,
        Integer sortOrder,
        List<LessonResourceResponse> resources
) {
}
