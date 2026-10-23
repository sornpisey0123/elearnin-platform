package com.pisey.elearning.repository.projection;

/** A category with the number of its PUBLISHED courses. */
public interface CategoryCourseCountView {

    Long getId();

    String getName();

    String getSlug();

    String getDescription();

    long getCourseCount();
}
