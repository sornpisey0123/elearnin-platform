package com.pisey.elearning.repository;

import com.pisey.elearning.entity.Category;
import com.pisey.elearning.entity.Course;
import com.pisey.elearning.entity.User;
import com.pisey.elearning.entity.enums.CourseLevel;
import com.pisey.elearning.entity.enums.CourseStatus;
import com.pisey.elearning.entity.enums.Role;
import com.pisey.elearning.repository.projection.CategoryCourseCountView;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs on the Flyway schema, so the V2 seed categories (Programming, Design, Business) are present.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CategoryRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Category programming;
    private Category design;

    @BeforeEach
    void setUp() {
        programming = categoryRepository.findBySlug("programming").orElseThrow();
        design = categoryRepository.findBySlug("design").orElseThrow();

        User instructor = entityManager.persist(User.builder()
                .keycloakId(UUID.randomUUID())
                .role(Role.INSTRUCTOR)
                .email("instructor@elearning.local")
                .fullName("Dara Chan")
                .build());

        course(instructor, programming, "java-basics", CourseStatus.PUBLISHED);
        course(instructor, programming, "spring-boot", CourseStatus.PUBLISHED);
        course(instructor, programming, "draft-course", CourseStatus.DRAFT);
        course(instructor, programming, "old-course", CourseStatus.ARCHIVED);
        course(instructor, design, "figma-draft", CourseStatus.DRAFT);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void courseCountIncludesOnlyPublishedCourses() {
        Map<String, Long> counts = categoryRepository.findAllWithCourseCount(CourseStatus.PUBLISHED).stream()
                .collect(Collectors.toMap(CategoryCourseCountView::getSlug, CategoryCourseCountView::getCourseCount));

        assertThat(counts).containsEntry("programming", 2L)
                .containsEntry("design", 0L)
                .containsEntry("business", 0L);
    }

    @Test
    void courseCountListIsSortedByName() {
        assertThat(categoryRepository.findAllWithCourseCount(CourseStatus.PUBLISHED))
                .extracting(CategoryCourseCountView::getName)
                .containsExactly("Business", "Design", "Programming");
    }

    @Test
    void publishedCoursesPageExcludesOtherStatusesAndFetchesAssociations() {
        Page<Course> page = courseRepository.findByCategoryIdAndStatus(
                programming.getId(), CourseStatus.PUBLISHED, PageRequest.of(0, 12, Sort.by("slug")));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Course::getSlug).containsExactly("java-basics", "spring-boot");
        Course first = page.getContent().getFirst();
        assertThat(Hibernate.isInitialized(first.getInstructor())).isTrue();
        assertThat(Hibernate.isInitialized(first.getCategory())).isTrue();
    }

    @Test
    void inUseCheckCountsDraftCourses() {
        assertThat(courseRepository.existsByCategoryId(design.getId())).isTrue();
        assertThat(courseRepository.existsByCategoryId(categoryRepository.findBySlug("business").orElseThrow().getId()))
                .isFalse();
    }

    @Test
    void publishedCountForOneCategory() {
        assertThat(courseRepository.countByCategoryIdAndStatus(programming.getId(), CourseStatus.PUBLISHED)).isEqualTo(2);
    }

    private void course(User instructor, Category category, String slug, CourseStatus status) {
        Course course = Course.builder()
                .instructor(instructor)
                .category(category)
                .title(slug)
                .slug(slug)
                .level(CourseLevel.BEGINNER)
                .price(BigDecimal.TEN)
                .build();
        if (status == CourseStatus.PUBLISHED) {
            course.publish();
        } else if (status == CourseStatus.ARCHIVED) {
            course.archive();
        }
        entityManager.persist(course);
    }
}
