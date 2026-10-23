package com.pisey.elearning.repository;

import com.pisey.elearning.dto.course.CourseSearchCriteria.CourseSort;
import com.pisey.elearning.entity.Category;
import com.pisey.elearning.entity.Course;
import com.pisey.elearning.entity.Enrollment;
import com.pisey.elearning.entity.Lesson;
import com.pisey.elearning.entity.Section;
import com.pisey.elearning.entity.User;
import com.pisey.elearning.entity.enums.CourseLevel;
import com.pisey.elearning.entity.enums.CourseStatus;
import com.pisey.elearning.entity.enums.EnrollmentStatus;
import com.pisey.elearning.entity.enums.LessonType;
import com.pisey.elearning.entity.enums.Role;
import com.pisey.elearning.repository.projection.CourseContentStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.pisey.elearning.repository.specification.CourseSpecifications.hasLevel;
import static com.pisey.elearning.repository.specification.CourseSpecifications.hasStatus;
import static com.pisey.elearning.repository.specification.CourseSpecifications.inCategory;
import static com.pisey.elearning.repository.specification.CourseSpecifications.keywordMatches;
import static com.pisey.elearning.repository.specification.CourseSpecifications.ownedBy;
import static com.pisey.elearning.repository.specification.CourseSpecifications.priceAtLeast;
import static com.pisey.elearning.repository.specification.CourseSpecifications.priceAtMost;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CourseRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User dara;
    private User sophea;
    private User student;
    private Category programming;
    private Category design;
    private Course java;
    private Course spring;

    @BeforeEach
    void setUp() {
        programming = categoryRepository.findBySlug("programming").orElseThrow();
        design = categoryRepository.findBySlug("design").orElseThrow();
        dara = user("dara@x", Role.INSTRUCTOR);
        sophea = user("sophea@x", Role.INSTRUCTOR);
        student = user("student@x", Role.STUDENT);

        // title/description, category, level, price, rating, enrollments, published days ago, status
        java = course(dara, programming, "Java Basics", "Start coding", CourseLevel.BEGINNER, "10.00",
                "4.50", 30, 3, CourseStatus.PUBLISHED);
        spring = course(dara, programming, "Spring Boot", "Build REST APIs with JAVA", CourseLevel.ADVANCED, "49.99",
                "4.90", 10, 1, CourseStatus.PUBLISHED);
        course(sophea, design, "Figma 101", "UI design", CourseLevel.BEGINNER, "0.00",
                "3.00", 99, 2, CourseStatus.PUBLISHED);
        course(dara, programming, "Java Draft", "secret", CourseLevel.BEGINNER, "5.00",
                "0", 0, 0, CourseStatus.DRAFT);
        course(sophea, design, "Old Java", "archived", CourseLevel.BEGINNER, "5.00",
                "0", 0, 5, CourseStatus.ARCHIVED);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void catalogueShowsPublishedOnly() {
        assertThat(titles(hasStatus(CourseStatus.PUBLISHED), CourseSort.NEWEST))
                .containsExactly("Spring Boot", "Figma 101", "Java Basics");
    }

    @Test
    void keywordMatchesTitleOrDescriptionCaseInsensitively() {
        assertThat(titles(Specification.allOf(hasStatus(CourseStatus.PUBLISHED), keywordMatches("java")), CourseSort.NEWEST))
                .containsExactly("Spring Boot", "Java Basics");
    }

    @Test
    void filtersByCategoryLevelAndPriceRange() {
        assertThat(titles(Specification.allOf(hasStatus(CourseStatus.PUBLISHED), inCategory(design.getId())), CourseSort.NEWEST))
                .containsExactly("Figma 101");
        assertThat(titles(Specification.allOf(hasStatus(CourseStatus.PUBLISHED), hasLevel(CourseLevel.ADVANCED)), CourseSort.NEWEST))
                .containsExactly("Spring Boot");
        assertThat(titles(Specification.allOf(hasStatus(CourseStatus.PUBLISHED),
                priceAtLeast(new BigDecimal("10.00")), priceAtMost(new BigDecimal("20"))), CourseSort.NEWEST))
                .containsExactly("Java Basics");
    }

    @Test
    void ratingAndPopularSorts() {
        assertThat(titles(hasStatus(CourseStatus.PUBLISHED), CourseSort.RATING))
                .containsExactly("Spring Boot", "Java Basics", "Figma 101");
        assertThat(titles(hasStatus(CourseStatus.PUBLISHED), CourseSort.POPULAR))
                .containsExactly("Figma 101", "Java Basics", "Spring Boot");
    }

    @Test
    void ownedByListsAllStatusesOfOneInstructor() {
        assertThat(titles(ownedBy(dara.getKeycloakId()), CourseSort.NEWEST))
                .containsExactlyInAnyOrder("Java Basics", "Spring Boot", "Java Draft");
    }

    @Test
    void contentStatsCountsSectionsLessonsAndVideoDuration() {
        Course course = entityManager.find(Course.class, java.getId());
        Section intro = section(course, "Intro", 1);
        Section empty = section(course, "Coming soon", 2);
        lesson(intro, "Welcome", 1, LessonType.VIDEO, 300);
        lesson(intro, "Setup", 2, LessonType.VIDEO, 600);
        lesson(intro, "Notes", 3, LessonType.TEXT, null);
        entityManager.persist(intro);
        entityManager.persist(empty);
        entityManager.flush();

        CourseContentStats stats = courseRepository.findContentStats(java.getId());

        assertThat(stats.getSectionCount()).isEqualTo(2);
        assertThat(stats.getLessonCount()).isEqualTo(3);
        assertThat(stats.getTotalDurationSeconds()).isEqualTo(900);
    }

    @Test
    void contentStatsAreZeroForEmptyCourse() {
        CourseContentStats stats = courseRepository.findContentStats(spring.getId());

        assertThat(stats.getSectionCount()).isZero();
        assertThat(stats.getLessonCount()).isZero();
        assertThat(stats.getTotalDurationSeconds()).isZero();
    }

    @Test
    void enrollmentChecks() {
        Course course = entityManager.find(Course.class, java.getId());
        entityManager.persist(Enrollment.builder()
                .student(entityManager.find(User.class, student.getId()))
                .course(course)
                .pricePaid(BigDecimal.TEN)
                .status(EnrollmentStatus.COMPLETED)
                .build());
        entityManager.flush();

        assertThat(enrollmentRepository.existsByCourseId(java.getId())).isTrue();
        assertThat(enrollmentRepository.existsByCourseIdAndStatus(java.getId(), EnrollmentStatus.ACTIVE)).isFalse();
        assertThat(enrollmentRepository.existsByStudentKeycloakIdAndCourseIdAndStatusIn(student.getKeycloakId(),
                java.getId(), List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED))).isTrue();
        assertThat(enrollmentRepository.existsByStudentKeycloakIdAndCourseIdAndStatusIn(dara.getKeycloakId(),
                java.getId(), List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED))).isFalse();
    }

    private List<String> titles(Specification<Course> spec, CourseSort sort) {
        return courseRepository.findAll(spec, PageRequest.of(0, 50, sort.sort()))
                .map(Course::getTitle)
                .getContent();
    }

    private User user(String email, Role role) {
        return entityManager.persist(User.builder()
                .keycloakId(UUID.randomUUID())
                .role(role)
                .email(email)
                .fullName(email)
                .build());
    }

    private Course course(User instructor, Category category, String title, String description, CourseLevel level,
                          String price, String rating, int enrollments, int publishedDaysAgo, CourseStatus status) {
        Course course = Course.builder()
                .instructor(instructor)
                .category(category)
                .title(title)
                .slug(title.toLowerCase().replace(' ', '-'))
                .description(description)
                .level(level)
                .price(new BigDecimal(price))
                .avgRating(new BigDecimal(rating))
                .enrollmentCount(enrollments)
                .status(status)
                .publishedAt(status == CourseStatus.DRAFT ? null : Instant.now().minusSeconds(publishedDaysAgo * 86_400L))
                .build();
        return entityManager.persist(course);
    }

    private static Section section(Course course, String title, int sortOrder) {
        Section section = Section.builder().title(title).sortOrder(sortOrder).build();
        course.addSection(section);
        return section;
    }

    private static void lesson(Section section, String title, int sortOrder, LessonType type, Integer duration) {
        section.addLesson(Lesson.builder()
                .title(title)
                .sortOrder(sortOrder)
                .type(type)
                .durationSeconds(duration)
                .build());
    }
}
