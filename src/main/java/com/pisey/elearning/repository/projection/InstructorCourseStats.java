package com.pisey.elearning.repository.projection;

import com.pisey.elearning.entity.enums.CourseStatus;

import java.math.BigDecimal;

/** One of an instructor's courses with its enrollment counts and revenue. */
public interface InstructorCourseStats {

    Long getCourseId();

    String getTitle();

    CourseStatus getStatus();

    long getEnrollments();

    long getCompletedEnrollments();

    BigDecimal getRevenue();

    BigDecimal getAvgRating();

    int getReviewCount();
}
