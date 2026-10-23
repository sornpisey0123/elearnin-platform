package com.pisey.elearning.service;

import com.pisey.elearning.entity.Course;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Who may see or manage a course. {@code caller} is the request's token, or null for anonymous requests.
 */
public interface CourseAccessService {

    /** The course's instructor, or any ADMIN. */
    boolean isOwnerOrAdmin(Course course, Jwt caller);

    /** Throws 403 unless {@link #isOwnerOrAdmin}. */
    void requireOwnerOrAdmin(Course course, Jwt caller);

    /** Enrolled with status ACTIVE or COMPLETED. */
    boolean isEnrolled(Course course, Jwt caller);

    /**
     * PUBLISHED courses for everyone; any status for owner/admin; ARCHIVED also for enrolled students.
     * Callers answer "not visible" with 404 so unpublished courses are not revealed.
     */
    boolean isVisible(Course course, Jwt caller);
}
