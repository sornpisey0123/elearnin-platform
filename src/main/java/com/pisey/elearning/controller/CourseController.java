package com.pisey.elearning.controller;

import com.pisey.elearning.dto.common.PageResponse;
import com.pisey.elearning.dto.course.CourseDetailResponse;
import com.pisey.elearning.dto.course.CourseSearchCriteria;
import com.pisey.elearning.dto.course.CourseSearchCriteria.CourseSort;
import com.pisey.elearning.dto.course.CourseSummaryResponse;
import com.pisey.elearning.entity.enums.CourseLevel;
import com.pisey.elearning.service.CourseService;
import com.pisey.elearning.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Public catalogue. A token is optional; when present it lets owners, admins and enrolled students
 * see courses that are not published.
 */
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
@Tag(name = "Courses")
public class CourseController {

    private final CourseService courseService;

    /** Paging is parsed here because {@code sort} is a named option, not Spring's {@code property,dir} syntax. */
    @GetMapping
    @Operation(summary = "Search published courses")
    public PageResponse<CourseSummaryResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) CourseLevel level,
            @RequestParam(required = false) @PositiveOrZero BigDecimal minPrice,
            @RequestParam(required = false) @PositiveOrZero BigDecimal maxPrice,
            @Parameter(description = "newest (default), rating or popular")
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "12") int size) {
        CourseSearchCriteria criteria = new CourseSearchCriteria(keyword, categoryId, level, minPrice, maxPrice);
        return courseService.search(criteria, CourseSort.from(sort), page, size);
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Course detail (unpublished courses only for owner, admin, or enrolled students if archived)")
    public CourseDetailResponse findBySlug(@PathVariable String slug) {
        return courseService.findBySlug(slug, SecurityUtils.currentJwt().orElse(null));
    }
}
