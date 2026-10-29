package com.pisey.elearning.dto.quiz;

import java.time.Instant;

/**
 * {@code expiresAt} is null when the quiz has no time limit. {@code resumed} is true when an open, unexpired attempt
 * was returned instead of starting a new one.
 */
public record AttemptStartResponse(
        Long attemptId,
        Long quizId,
        Instant startedAt,
        Instant expiresAt,
        boolean resumed
) {
}
