package com.pisey.elearning.dto.quiz;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Graded attempt. A question earns its points only if the selected options equal the correct ones exactly.
 * {@code timedOut} attempts score 0 and fail regardless of the answers.
 */
public record AttemptResultResponse(
        Long attemptId,
        Long quizId,
        BigDecimal score,
        boolean passed,
        boolean timedOut,
        int earnedPoints,
        int totalPoints,
        Instant startedAt,
        Instant submittedAt,
        List<QuestionResult> questions
) {

    public record QuestionResult(Long questionId, boolean correct, int points, int earnedPoints,
                                 List<Long> selectedOptionIds, List<Long> correctOptionIds) {
    }
}
