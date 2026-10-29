package com.pisey.elearning.dto.quiz;

/** A quiz in a course's quiz list; exactly one of lessonId/sectionId is set. */
public record QuizSummaryResponse(
        Long id,
        Long lessonId,
        Long sectionId,
        String title,
        Integer passMark,
        Integer timeLimitMinutes
) {
}
