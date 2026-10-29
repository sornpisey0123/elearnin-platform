package com.pisey.elearning.controller;

import com.pisey.elearning.dto.common.PageResponse;
import com.pisey.elearning.dto.review.ReviewCreateRequest;
import com.pisey.elearning.dto.review.ReviewResponse;
import com.pisey.elearning.dto.review.ReviewUpdateRequest;
import com.pisey.elearning.service.ReviewService;
import com.pisey.elearning.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Course reviews; the list is public (see SecurityConfig), writes need a token. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Reviews")
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/api/courses/{courseId}/reviews")
    @Operation(summary = "Reviews of a course with their authors (paged, newest first by default)")
    public PageResponse<ReviewResponse> findByCourse(
            @PathVariable Long courseId,
            @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return reviewService.findByCourse(courseId, pageable, SecurityUtils.currentJwt().orElse(null));
    }

    @PostMapping("/api/courses/{courseId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Review a course you are enrolled in (one review per course)")
    public ReviewResponse create(@PathVariable Long courseId,
                                 @Valid @RequestBody ReviewCreateRequest request,
                                 @AuthenticationPrincipal Jwt jwt) {
        return reviewService.create(courseId, request, jwt);
    }

    @PutMapping("/api/reviews/{id}")
    @Operation(summary = "Edit your review")
    public ReviewResponse update(@PathVariable Long id,
                                 @Valid @RequestBody ReviewUpdateRequest request,
                                 @AuthenticationPrincipal Jwt jwt) {
        return reviewService.update(id, request, jwt);
    }

    @DeleteMapping("/api/reviews/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete your review (admins can delete any)")
    public void delete(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        reviewService.delete(id, jwt);
    }

    @GetMapping("/api/me/courses/{courseId}/review")
    @Operation(summary = "My review of a course, or 404")
    public ReviewResponse findMine(@PathVariable Long courseId, @AuthenticationPrincipal Jwt jwt) {
        return reviewService.findMine(courseId, jwt);
    }
}
