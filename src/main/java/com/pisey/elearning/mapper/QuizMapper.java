package com.pisey.elearning.mapper;

import com.pisey.elearning.dto.quiz.AttemptSummaryResponse;
import com.pisey.elearning.dto.quiz.QuizResponse;
import com.pisey.elearning.dto.quiz.QuizSummaryResponse;
import com.pisey.elearning.dto.quiz.StudentQuizResponse;
import com.pisey.elearning.entity.Quiz;
import com.pisey.elearning.entity.QuizAttempt;
import com.pisey.elearning.entity.QuizOption;
import com.pisey.elearning.entity.QuizQuestion;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface QuizMapper {

    @Mapping(target = "courseId", source = "course.id")
    @Mapping(target = "lessonId", source = "lesson.id")
    @Mapping(target = "sectionId", source = "section.id")
    QuizResponse toResponse(Quiz quiz);

    QuizResponse.QuestionResponse toQuestionResponse(QuizQuestion question);

    QuizResponse.OptionResponse toOptionResponse(QuizOption option);

    /** Student view: the option type has no {@code correct} field, so answers can't leak. */
    @Mapping(target = "courseId", source = "course.id")
    @Mapping(target = "lessonId", source = "lesson.id")
    @Mapping(target = "sectionId", source = "section.id")
    @Mapping(target = "totalPoints", expression = "java(totalPoints(quiz))")
    StudentQuizResponse toStudentResponse(Quiz quiz);

    StudentQuizResponse.QuestionView toQuestionView(QuizQuestion question);

    StudentQuizResponse.OptionView toOptionView(QuizOption option);

    @Mapping(target = "lessonId", source = "lesson.id")
    @Mapping(target = "sectionId", source = "section.id")
    QuizSummaryResponse toSummary(Quiz quiz);

    @Mapping(target = "attemptId", source = "id")
    @Mapping(target = "quizId", source = "quiz.id")
    AttemptSummaryResponse toAttemptSummary(QuizAttempt attempt);

    default int totalPoints(Quiz quiz) {
        return quiz.getQuestions().stream().mapToInt(QuizQuestion::getPoints).sum();
    }
}
