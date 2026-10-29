package com.pisey.elearning.dto.quiz;

import com.pisey.elearning.entity.enums.QuestionType;

import java.util.List;

/** Instructor view of a quiz, including which options are correct. Exactly one of lessonId/sectionId is set. */
public record QuizResponse(
        Long id,
        Long courseId,
        Long lessonId,
        Long sectionId,
        String title,
        Integer passMark,
        Integer timeLimitMinutes,
        List<QuestionResponse> questions
) {

    public record QuestionResponse(Long id, String questionText, QuestionType type, Integer points, Integer sortOrder,
                                   List<OptionResponse> options) {
    }

    public record OptionResponse(Long id, String optionText, boolean correct) {
    }
}
