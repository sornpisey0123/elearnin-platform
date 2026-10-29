package com.pisey.elearning.dto.report;

import com.pisey.elearning.entity.enums.Role;

import java.math.BigDecimal;
import java.util.Map;

/** Platform totals. {@code usersByRole} has every role, with 0 for roles without users. */
public record AdminOverviewResponse(
        Map<Role, Long> usersByRole,
        CourseCounts courses,
        long totalEnrollments,
        BigDecimal totalRevenue
) {

    public record CourseCounts(long total, long published, long draft, long archived) {
    }
}
