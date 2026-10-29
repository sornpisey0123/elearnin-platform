package com.pisey.elearning.repository.projection;

import com.pisey.elearning.entity.enums.CourseStatus;

/** Number of courses with one status. */
public interface StatusCount {

    CourseStatus getStatus();

    long getCount();
}
