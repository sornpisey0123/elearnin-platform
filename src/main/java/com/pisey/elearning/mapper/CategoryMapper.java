package com.pisey.elearning.mapper;

import com.pisey.elearning.dto.category.CategoryResponse;
import com.pisey.elearning.entity.Category;
import com.pisey.elearning.repository.projection.CategoryCourseCountView;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    CategoryResponse toResponse(CategoryCourseCountView view);

    CategoryResponse toResponse(Category category, long courseCount);
}
