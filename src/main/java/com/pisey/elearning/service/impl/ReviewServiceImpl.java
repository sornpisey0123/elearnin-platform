package com.pisey.elearning.service.impl;

import com.pisey.elearning.dto.common.PageResponse;
import com.pisey.elearning.dto.review.ReviewCreateRequest;
import com.pisey.elearning.dto.review.ReviewResponse;
import com.pisey.elearning.dto.review.ReviewUpdateRequest;
import com.pisey.elearning.entity.Course;
import com.pisey.elearning.entity.Review;
import com.pisey.elearning.entity.User;
import com.pisey.elearning.exception.ConflictException;
import com.pisey.elearning.exception.ForbiddenException;
import com.pisey.elearning.exception.ResourceNotFoundException;
import com.pisey.elearning.mapper.ReviewMapper;
import com.pisey.elearning.repository.CourseRepository;
import com.pisey.elearning.repository.ReviewRepository;
import com.pisey.elearning.service.CourseAccessService;
import com.pisey.elearning.service.CurrentUserService;
import com.pisey.elearning.service.ReviewService;
import com.pisey.elearning.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final CourseRepository courseRepository;
    private final CourseAccessService courseAccessService;
    private final CurrentUserService currentUserService;
    private final ReviewMapper reviewMapper;

    @Override
    public PageResponse<ReviewResponse> findByCourse(Long courseId, Pageable pageable, Jwt caller) {
        getVisibleCourse(courseId, caller);
        return PageResponse.from(reviewRepository.findByCourseId(courseId, pageable), reviewMapper::toResponse);
    }

    @Override
    @Transactional
    public ReviewResponse create(Long courseId, ReviewCreateRequest request, Jwt caller) {
        Course course = getVisibleCourse(courseId, caller);
        if (!courseAccessService.isEnrolled(course, caller)) {
            throw new ForbiddenException("Only enrolled students can review this course");
        }
        User student = currentUserService.getOrCreate(caller);
        if (reviewRepository.existsByCourseIdAndStudentId(courseId, student.getId())) {
            throw new ConflictException("You already reviewed this course");
        }

        Review review;
        try {
            review = reviewRepository.saveAndFlush(Review.builder()
                    .course(course)
                    .student(student)
                    .rating(request.rating().shortValue())
                    .comment(normalize(request.comment()))
                    .build());
        } catch (DataIntegrityViolationException ex) {
            // A concurrent request reviewed first; the unique (course_id, student_id) constraint caught it
            throw new ConflictException("You already reviewed this course");
        }
        reviewRepository.recalculateCourseRating(courseId);
        return reviewMapper.toResponse(review);
    }

    @Override
    @Transactional
    public ReviewResponse update(Long reviewId, ReviewUpdateRequest request, Jwt caller) {
        Review review = getReview(reviewId);
        if (!isAuthor(review, caller)) {
            throw new ForbiddenException("You can only edit your own review");
        }

        review.setRating(request.rating().shortValue());
        review.setComment(normalize(request.comment()));
        reviewRepository.recalculateCourseRating(review.getCourse().getId());
        return reviewMapper.toResponse(review);
    }

    @Override
    @Transactional
    public void delete(Long reviewId, Jwt caller) {
        Review review = getReview(reviewId);
        if (!isAuthor(review, caller) && !SecurityUtils.isAdmin()) {
            throw new ForbiddenException("You can only delete your own review");
        }

        Long courseId = review.getCourse().getId();
        reviewRepository.delete(review);
        reviewRepository.recalculateCourseRating(courseId);
    }

    @Override
    public ReviewResponse findMine(Long courseId, Jwt caller) {
        return reviewRepository.findByCourseIdAndStudentKeycloakId(courseId, SecurityUtils.keycloakId(caller))
                .map(reviewMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("You have not reviewed course " + courseId));
    }

    // --- helpers ---

    /** 404 for courses the caller can't see, so unpublished courses aren't revealed. */
    private Course getVisibleCourse(Long courseId, Jwt caller) {
        return courseRepository.findWithInstructorAndCategoryById(courseId)
                .filter(course -> courseAccessService.isVisible(course, caller))
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", courseId));
    }

    private Review getReview(Long reviewId) {
        return reviewRepository.findWithStudentAndCourseById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", "id", reviewId));
    }

    private static boolean isAuthor(Review review, Jwt caller) {
        return review.getStudent().getKeycloakId().equals(SecurityUtils.keycloakId(caller));
    }

    /** Trims the comment; a blank one is stored as null. */
    private static String normalize(String comment) {
        return StringUtils.hasText(comment) ? comment.trim() : null;
    }
}
