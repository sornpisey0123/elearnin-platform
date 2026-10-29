package com.pisey.elearning.dto.quiz;

import java.math.BigDecimal;
import java.time.Instant;

/** One of the caller's attempts; score, passed and submittedAt are null while the attempt is open. */
public record AttemptSummaryResponse(
        Long attemptId,
        Long quizId,
        BigDecimal score,
        Boolean passed,
        Instant startedAt,
        Instant submittedAt
) {
}
