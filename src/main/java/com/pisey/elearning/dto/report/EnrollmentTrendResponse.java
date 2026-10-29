package com.pisey.elearning.dto.report;

import java.math.BigDecimal;
import java.util.List;

/** One point per calendar month (UTC), oldest first, ending with the current month; empty months are zeros. */
public record EnrollmentTrendResponse(
        Long courseId,
        int months,
        List<MonthPoint> points
) {

    /** {@code month} is {@code YYYY-MM}. */
    public record MonthPoint(String month, long enrollments, BigDecimal revenue) {
    }
}
