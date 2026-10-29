package com.pisey.elearning.repository.projection;

import com.pisey.elearning.entity.enums.CourseStatus;

import java.math.BigDecimal;

/** A course row for the admin's top-courses ranking, from the cached counters on the course. */
public interface TopCourseView {

    Long getId();

    String getTitle();

    String getSlug();

    CourseStatus getStatus();

    String getInstructorName();

    int getEnrollmentCount();

    BigDecimal getAvgRating();

    int getReviewCount();
}
