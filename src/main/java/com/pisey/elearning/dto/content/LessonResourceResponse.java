package com.pisey.elearning.dto.content;

public record LessonResourceResponse(
        Long id,
        String title,
        String fileUrl,
        String mimeType,
        Long sizeBytes
) {
}
