package com.pisey.elearning.repository.projection;

/** Outline size of one course. */
public interface CourseContentStats {

    long getSectionCount();

    long getLessonCount();

    /** Sum of VIDEO lesson durations. */
    long getTotalDurationSeconds();
}
