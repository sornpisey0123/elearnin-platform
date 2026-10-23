package com.pisey.elearning.dto.course;

/** Returned with 200 when a delete archived the course instead of removing it. */
public record CourseDeleteResponse(boolean archived) {
}
