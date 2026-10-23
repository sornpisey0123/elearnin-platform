package com.pisey.elearning.service;

import com.pisey.elearning.dto.common.PageResponse;
import com.pisey.elearning.dto.course.CourseCreateRequest;
import com.pisey.elearning.dto.course.CourseDetailResponse;
import com.pisey.elearning.dto.course.CourseSearchCriteria;
import com.pisey.elearning.dto.course.CourseSearchCriteria.CourseSort;
import com.pisey.elearning.dto.course.CourseSummaryResponse;
import com.pisey.elearning.dto.course.CourseUpdateRequest;
import com.pisey.elearning.dto.course.InstructorCourseResponse;
import com.pisey.elearning.entity.enums.CourseStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.multipart.MultipartFile;

/**
 * Instructor methods take the caller's token: they act only on the caller's own courses, except for ADMIN.
 */
public interface CourseService {

    // --- public catalogue ---

    /** PUBLISHED courses only. */
    PageResponse<CourseSummaryResponse> search(CourseSearchCriteria criteria, CourseSort sort, int page, int size);

    /**
     * PUBLISHED courses for everyone; any status for the owner or an admin; ARCHIVED also for enrolled
     * students. Anything else is 404 so unpublished courses are not revealed. {@code caller} may be null.
     */
    CourseDetailResponse findBySlug(String slug, Jwt caller);

    // --- instructor ---

    PageResponse<InstructorCourseResponse> findOwn(Jwt caller, CourseStatus status, Pageable pageable);

    CourseDetailResponse create(Jwt caller, CourseCreateRequest request);

    CourseDetailResponse update(Long id, CourseUpdateRequest request, Jwt caller);

    /** jpg/png/webp up to 5 MB; the previous thumbnail file is deleted once the change commits. */
    CourseDetailResponse updateThumbnail(Long id, MultipartFile file, Jwt caller);

    /** 400 unless the course has at least one section containing at least one lesson. */
    CourseDetailResponse publish(Long id, Jwt caller);

    /** Back to DRAFT; 409 while the course has ACTIVE enrollments. */
    CourseDetailResponse unpublish(Long id, Jwt caller);

    /**
     * Archives a course that has enrollments (they hold the revenue history) and returns {@code true};
     * hard-deletes one that never had any and returns {@code false}.
     */
    boolean delete(Long id, Jwt caller);
}
