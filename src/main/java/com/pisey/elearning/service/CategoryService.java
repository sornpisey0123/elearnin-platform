package com.pisey.elearning.service;

import com.pisey.elearning.dto.category.CategoryCreateRequest;
import com.pisey.elearning.dto.category.CategoryResponse;
import com.pisey.elearning.dto.category.CategoryUpdateRequest;
import com.pisey.elearning.dto.common.PageResponse;
import com.pisey.elearning.dto.course.CourseSummaryResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CategoryService {

    /** All categories by name, each with its number of PUBLISHED courses. */
    List<CategoryResponse> findAll();

    /** PUBLISHED courses in the category with this slug. */
    PageResponse<CourseSummaryResponse> findPublishedCourses(String slug, Pageable pageable);

    CategoryResponse create(CategoryCreateRequest request);

    CategoryResponse update(Long id, CategoryUpdateRequest request);

    /** Fails with 409 while any course (in any status) uses the category. */
    void delete(Long id);
}
