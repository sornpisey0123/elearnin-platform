package com.pisey.elearning.controller;

import com.pisey.elearning.dto.common.PageResponse;
import com.pisey.elearning.dto.course.CourseCreateRequest;
import com.pisey.elearning.dto.course.CourseDeleteResponse;
import com.pisey.elearning.dto.course.CourseDetailResponse;
import com.pisey.elearning.dto.course.CourseUpdateRequest;
import com.pisey.elearning.dto.course.InstructorCourseResponse;
import com.pisey.elearning.entity.enums.CourseStatus;
import com.pisey.elearning.service.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** The caller's own courses; ADMIN may act on any course. */
@RestController
@RequestMapping("/api/instructor/courses")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
@Tag(name = "Instructor: courses")
public class InstructorCourseController {

    private final CourseService courseService;

    @GetMapping
    @Operation(summary = "My courses (all statuses, optional status filter)")
    public PageResponse<InstructorCourseResponse> findOwn(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) CourseStatus status,
            @SortDefault(sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return courseService.findOwn(jwt, status, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a DRAFT course")
    public CourseDetailResponse create(@AuthenticationPrincipal Jwt jwt,
                                       @Valid @RequestBody CourseCreateRequest request) {
        return courseService.create(jwt, request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update my course")
    public CourseDetailResponse update(@PathVariable Long id,
                                       @Valid @RequestBody CourseUpdateRequest request,
                                       @AuthenticationPrincipal Jwt jwt) {
        return courseService.update(id, request, jwt);
    }

    @PostMapping(path = "/{id}/thumbnail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload thumbnail (jpg, png or webp, max 5 MB)")
    public CourseDetailResponse uploadThumbnail(@PathVariable Long id,
                                                @RequestPart("file") MultipartFile file,
                                                @AuthenticationPrincipal Jwt jwt) {
        return courseService.updateThumbnail(id, file, jwt);
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "Publish (needs a section with at least one lesson)")
    public CourseDetailResponse publish(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return courseService.publish(id, jwt);
    }

    @PostMapping("/{id}/unpublish")
    @Operation(summary = "Back to draft (409 while students are actively enrolled)")
    public CourseDetailResponse unpublish(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return courseService.unpublish(id, jwt);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete; a course with enrollments is archived instead (200 {archived:true})")
    public ResponseEntity<CourseDeleteResponse> delete(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        if (courseService.delete(id, jwt)) {
            return ResponseEntity.ok(new CourseDeleteResponse(true));
        }
        return ResponseEntity.noContent().build();
    }
}
