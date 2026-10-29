package com.pisey.elearning.repository.projection;

import java.math.BigDecimal;

/** Platform-wide enrollment count and the sum of the prices paid. */
public interface EnrollmentTotals {

    long getEnrollments();

    BigDecimal getRevenue();
}
