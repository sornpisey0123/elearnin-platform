package com.pisey.elearning.service.impl;

import com.pisey.elearning.dto.category.CategoryCreateRequest;
import com.pisey.elearning.dto.category.CategoryResponse;
import com.pisey.elearning.dto.category.CategoryUpdateRequest;
import com.pisey.elearning.dto.common.PageResponse;
import com.pisey.elearning.dto.course.CourseSummaryResponse;
import com.pisey.elearning.entity.Category;
import com.pisey.elearning.entity.Course;
import com.pisey.elearning.entity.User;
import com.pisey.elearning.entity.enums.CourseLevel;
import com.pisey.elearning.entity.enums.CourseStatus;
import com.pisey.elearning.exception.BadRequestException;
import com.pisey.elearning.exception.ConflictException;
import com.pisey.elearning.exception.ResourceNotFoundException;
import com.pisey.elearning.mapper.CategoryMapperImpl;
import com.pisey.elearning.mapper.CourseMapperImpl;
import com.pisey.elearning.repository.CategoryRepository;
import com.pisey.elearning.repository.CourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private CourseRepository courseRepository;

    private CategoryServiceImpl categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryServiceImpl(categoryRepository, courseRepository,
                new CategoryMapperImpl(), new CourseMapperImpl());
    }

    @Test
    void createGeneratesSlugFromName() {
        given(categoryRepository.save(any(Category.class))).willAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = categoryService.create(new CategoryCreateRequest("  Data Science & AI ", " ML "));

        assertThat(response.name()).isEqualTo("Data Science & AI");
        assertThat(response.slug()).isEqualTo("data-science-ai");
        assertThat(response.description()).isEqualTo("ML");
        assertThat(response.courseCount()).isZero();
    }

    @Test
    void createAppendsSuffixWhenSlugIsTaken() {
        // "C++" and "C" both slugify to "c"
        given(categoryRepository.existsBySlug("c")).willReturn(true);
        given(categoryRepository.save(any(Category.class))).willAnswer(invocation -> invocation.getArgument(0));

        assertThat(categoryService.create(new CategoryCreateRequest("C++", null)).slug()).isEqualTo("c-2");
    }

    @Test
    void createRejectsDuplicateNameIgnoringCase() {
        given(categoryRepository.existsByNameIgnoreCase("programming")).willReturn(true);

        assertThatThrownBy(() -> categoryService.create(new CategoryCreateRequest("programming", null)))
                .isInstanceOf(ConflictException.class);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void createRejectsNameWithoutLettersOrDigits() {
        assertThatThrownBy(() -> categoryService.create(new CategoryCreateRequest("!!!", null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateRenameRegeneratesSlugAndReturnsPublishedCount() {
        Category category = category(1L, "Programming", "programming");
        given(categoryRepository.findById(1L)).willReturn(Optional.of(category));
        given(courseRepository.countByCategoryIdAndStatus(1L, CourseStatus.PUBLISHED)).willReturn(4L);

        CategoryResponse response = categoryService.update(1L, new CategoryUpdateRequest("Software Development", null));

        assertThat(response.slug()).isEqualTo("software-development");
        assertThat(response.courseCount()).isEqualTo(4);
        verify(categoryRepository).saveAndFlush(category);
    }

    @Test
    void updateWithSameNameKeepsSlug() {
        Category category = category(1L, "Programming", "programming-old");
        given(categoryRepository.findById(1L)).willReturn(Optional.of(category));

        CategoryResponse response = categoryService.update(1L, new CategoryUpdateRequest("Programming", "New text"));

        assertThat(response.slug()).isEqualTo("programming-old");
        assertThat(response.description()).isEqualTo("New text");
        verify(categoryRepository, never()).existsBySlugAndIdNot(anyString(), anyLong());
    }

    @Test
    void updateRejectsNameOfAnotherCategory() {
        given(categoryRepository.findById(1L)).willReturn(Optional.of(category(1L, "Programming", "programming")));
        given(categoryRepository.existsByNameIgnoreCaseAndIdNot("Design", 1L)).willReturn(true);

        assertThatThrownBy(() -> categoryService.update(1L, new CategoryUpdateRequest("Design", null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateUnknownIs404() {
        given(categoryRepository.findById(9L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.update(9L, new CategoryUpdateRequest("X", null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteInUseIs409() {
        given(categoryRepository.findById(1L)).willReturn(Optional.of(category(1L, "Programming", "programming")));
        given(courseRepository.existsByCategoryId(1L)).willReturn(true);

        assertThatThrownBy(() -> categoryService.delete(1L)).isInstanceOf(ConflictException.class);
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void deleteUnusedCategory() {
        Category category = category(3L, "Business", "business");
        given(categoryRepository.findById(3L)).willReturn(Optional.of(category));

        categoryService.delete(3L);

        verify(categoryRepository).delete(category);
    }

    @Test
    void publishedCoursesMapsSummaries() {
        Category category = category(1L, "Programming", "programming");
        User instructor = User.builder().fullName("Dara Chan").build();
        Course course = Course.builder()
                .instructor(instructor)
                .category(category)
                .title("Java Basics")
                .slug("java-basics")
                .level(CourseLevel.BEGINNER)
                .price(new BigDecimal("19.99"))
                .build();
        course.setId(10L);
        Pageable pageable = PageRequest.of(0, 12);
        given(categoryRepository.findBySlug("programming")).willReturn(Optional.of(category));
        given(courseRepository.findByCategoryIdAndStatus(eq(1L), eq(CourseStatus.PUBLISHED), any()))
                .willReturn(new PageImpl<>(List.of(course), pageable, 1));

        PageResponse<CourseSummaryResponse> page = categoryService.findPublishedCourses("programming", pageable);

        CourseSummaryResponse summary = page.content().getFirst();
        assertThat(summary.instructorName()).isEqualTo("Dara Chan");
        assertThat(summary.categoryName()).isEqualTo("Programming");
        assertThat(summary.price()).isEqualByComparingTo("19.99");
        assertThat(page.totalElements()).isEqualTo(1);

        ArgumentCaptor<CourseStatus> status = ArgumentCaptor.forClass(CourseStatus.class);
        verify(courseRepository).findByCategoryIdAndStatus(eq(1L), status.capture(), eq(pageable));
        assertThat(status.getValue()).isEqualTo(CourseStatus.PUBLISHED);
    }

    @Test
    void publishedCoursesUnknownSlugIs404() {
        given(categoryRepository.findBySlug("nope")).willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.findPublishedCourses("nope", PageRequest.of(0, 12)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static Category category(Long id, String name, String slug) {
        Category category = Category.builder().name(name).slug(slug).build();
        category.setId(id);
        return category;
    }
}
