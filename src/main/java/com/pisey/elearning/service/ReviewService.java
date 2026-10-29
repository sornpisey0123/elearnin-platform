package com.pisey.elearning.service;

import com.pisey.elearning.dto.common.PageResponse;
import com.pisey.elearning.dto.review.ReviewCreateRequest;
import com.pisey.elearning.dto.review.ReviewResponse;
import com.pisey.elearning.dto.review.ReviewUpdateRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Course reviews. Every write recalculates the course's cached {@code avgRating} and {@code reviewCount} in the
 * same transaction.
 */
public interface ReviewService {

    /** Public, visible like the course itself; {@code caller} may be null. */
    PageResponse<ReviewResponse> findByCourse(Long courseId, Pageable pageable, Jwt caller);

    /** Enrolled students (ACTIVE or COMPLETED) only, else 403; one review per student and course, else 409. */
    ReviewResponse create(Long courseId, ReviewCreateRequest request, Jwt caller);

    /** Author only. */
    ReviewResponse update(Long reviewId, ReviewUpdateRequest request, Jwt caller);

    /** Author or ADMIN. */
    void delete(Long reviewId, Jwt caller);

    /** The caller's review of the course, or 404. */
    ReviewResponse findMine(Long courseId, Jwt caller);
}
