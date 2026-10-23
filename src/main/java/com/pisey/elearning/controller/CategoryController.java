package com.pisey.elearning.controller;

import com.pisey.elearning.dto.category.CategoryResponse;
import com.pisey.elearning.dto.common.PageResponse;
import com.pisey.elearning.dto.course.CourseSummaryResponse;
import com.pisey.elearning.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.SortDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public catalogue endpoints; no token required. */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@SecurityRequirements
@Tag(name = "Categories")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "List categories with their number of published courses")
    public List<CategoryResponse> findAll() {
        return categoryService.findAll();
    }

    @GetMapping("/{slug}/courses")
    @Operation(summary = "Published courses in a category (paged, newest first by default)")
    public PageResponse<CourseSummaryResponse> findCourses(
            @PathVariable String slug,
            @SortDefault(sort = "publishedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return categoryService.findPublishedCourses(slug, pageable);
    }
}
