package com.pisey.elearning.dto.quiz;

import com.pisey.elearning.entity.enums.QuestionType;

import java.util.List;

/** A quiz as a student sees it before answering. Never contains which options are correct. */
public record StudentQuizResponse(
        Long id,
        Long courseId,
        Long lessonId,
        Long sectionId,
        String title,
        Integer passMark,
        Integer timeLimitMinutes,
        int totalPoints,
        List<QuestionView> questions
) {

    public record QuestionView(Long id, String questionText, QuestionType type, Integer points, Integer sortOrder,
                               List<OptionView> options) {
    }

    public record OptionView(Long id, String optionText) {
    }
}
