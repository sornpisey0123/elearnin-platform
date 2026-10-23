package com.pisey.elearning.dto.enrollment;

import com.pisey.elearning.entity.enums.EnrollmentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record EnrollmentResponse(
        Long id,
        Long courseId,
        EnrollmentStatus status,
        BigDecimal pricePaid,
        BigDecimal progressPct,
        Instant enrolledAt,
        Instant completedAt
) {
}
