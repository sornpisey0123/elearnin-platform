package com.pisey.elearning.service.impl;

import com.pisey.elearning.dto.category.CategoryCreateRequest;
import com.pisey.elearning.dto.category.CategoryResponse;
import com.pisey.elearning.dto.category.CategoryUpdateRequest;
import com.pisey.elearning.dto.common.PageResponse;
import com.pisey.elearning.dto.course.CourseSummaryResponse;
import com.pisey.elearning.entity.Category;
import com.pisey.elearning.entity.enums.CourseStatus;
import com.pisey.elearning.exception.BadRequestException;
import com.pisey.elearning.exception.ConflictException;
import com.pisey.elearning.exception.ResourceNotFoundException;
import com.pisey.elearning.mapper.CategoryMapper;
import com.pisey.elearning.mapper.CourseMapper;
import com.pisey.elearning.repository.CategoryRepository;
import com.pisey.elearning.repository.CourseRepository;
import com.pisey.elearning.service.CategoryService;
import com.pisey.elearning.utils.SlugUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryService {

    private static final int SLUG_MAX_LENGTH = 120;

    private final CategoryRepository categoryRepository;
    private final CourseRepository courseRepository;
    private final CategoryMapper categoryMapper;
    private final CourseMapper courseMapper;

    @Override
    public List<CategoryResponse> findAll() {
        return categoryRepository.findAllWithCourseCount(CourseStatus.PUBLISHED).stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Override
    public PageResponse<CourseSummaryResponse> findPublishedCourses(String slug, Pageable pageable) {
        Category category = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "slug", slug));
        return PageResponse.from(
                courseRepository.findByCategoryIdAndStatus(category.getId(), CourseStatus.PUBLISHED, pageable),
                courseMapper::toSummaryResponse);
    }

    @Override
    @Transactional
    public CategoryResponse create(CategoryCreateRequest request) {
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Category name already exists: " + name);
        }

        Category category = Category.builder()
                .name(name)
                .slug(SlugUtils.uniqueSlug(baseSlug(name), SLUG_MAX_LENGTH, categoryRepository::existsBySlug))
                .description(trimToNull(request.description()))
                .build();
        return categoryMapper.toResponse(categoryRepository.save(category), 0);
    }

    @Override
    @Transactional
    public CategoryResponse update(Long id, CategoryUpdateRequest request) {
        Category category = getCategory(id);
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("Category name already exists: " + name);
        }

        // Keep the slug (and so public URLs) stable unless the name actually changes
        if (!category.getName().equals(name)) {
            category.setSlug(SlugUtils.uniqueSlug(baseSlug(name), SLUG_MAX_LENGTH,
                    slug -> categoryRepository.existsBySlugAndIdNot(slug, id)));
        }
        category.setName(name);
        category.setDescription(trimToNull(request.description()));
        categoryRepository.saveAndFlush(category);
        return categoryMapper.toResponse(category,
                courseRepository.countByCategoryIdAndStatus(id, CourseStatus.PUBLISHED));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Category category = getCategory(id);
        if (courseRepository.existsByCategoryId(id)) {
            throw new ConflictException("Category is still used by one or more courses");
        }
        categoryRepository.delete(category);
    }

    private Category getCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
    }

    private static String baseSlug(String name) {
        String slug = SlugUtils.slugify(name, SLUG_MAX_LENGTH);
        if (slug.isEmpty()) {
            throw new BadRequestException("Name must contain at least one letter or digit");
        }
        return slug;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
