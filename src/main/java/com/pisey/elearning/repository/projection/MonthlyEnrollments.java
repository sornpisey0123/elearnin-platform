package com.pisey.elearning.repository.projection;

import java.math.BigDecimal;

/** Enrollments in one calendar month (UTC); {@code month} is {@code YYYY-MM}. */
public interface MonthlyEnrollments {

    String getMonth();

    long getEnrollments();

    BigDecimal getRevenue();
}
