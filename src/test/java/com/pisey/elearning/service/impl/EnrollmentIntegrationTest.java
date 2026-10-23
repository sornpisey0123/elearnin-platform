package com.pisey.elearning.service.impl;

import com.pisey.elearning.config.JpaAuditingConfig;
import com.pisey.elearning.dto.enrollment.ContinueLearningResponse;
import com.pisey.elearning.dto.enrollment.LessonProgressResponse;
import com.pisey.elearning.dto.enrollment.MyCourseResponse;
import com.pisey.elearning.entity.Category;
import com.pisey.elearning.entity.Course;
import com.pisey.elearning.entity.Lesson;
import com.pisey.elearning.entity.Section;
import com.pisey.elearning.entity.User;
import com.pisey.elearning.entity.enums.CourseLevel;
import com.pisey.elearning.entity.enums.EnrollmentStatus;
import com.pisey.elearning.entity.enums.LessonType;
import com.pisey.elearning.entity.enums.Role;
import com.pisey.elearning.exception.ConflictException;
import com.pisey.elearning.mapper.CourseMapperImpl;
import com.pisey.elearning.mapper.EnrollmentMapperImpl;
import com.pisey.elearning.repository.CategoryRepository;
import com.pisey.elearning.service.CurrentUserService;
import com.pisey.elearning.service.EnrollmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/** The real enrollment queries (native upsert, NOT EXISTS ordering, atomic counter) on PostgreSQL. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({EnrollmentServiceImpl.class, EnrollmentMapperImpl.class, CourseMapperImpl.class, JpaAuditingConfig.class})
@Testcontainers
class EnrollmentIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private EnrollmentService enrollmentService;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private CurrentUserService currentUserService;

    private Jwt studentJwt;
    private Course course;
    private Lesson a1;
    private Lesson a2;
    private Lesson b1;

    @BeforeEach
    void setUp() {
        UUID studentId = UUID.randomUUID();
        User instructor = entityManager.persist(User.builder().keycloakId(UUID.randomUUID()).role(Role.INSTRUCTOR)
                .email("i@x").fullName("Dara").build());
        User student = entityManager.persist(User.builder().keycloakId(studentId).role(Role.STUDENT)
                .email("s@x").fullName("Vanna").build());
        Category category = categoryRepository.findBySlug("programming").orElseThrow();
        course = Course.builder().instructor(instructor).category(category).title("Java").slug("java")
                .level(CourseLevel.BEGINNER).price(new BigDecimal("19.99")).build();
        course.publish();
        entityManager.persist(course);

        // Section B is positioned before section A, so outline order is b1, a1, a2
        Section sectionA = section("A", 2);
        Section sectionB = section("B", 1);
        a1 = lesson(sectionA, "a1", 1);
        a2 = lesson(sectionA, "a2", 2);
        b1 = lesson(sectionB, "b1", 1);
        entityManager.flush();

        studentJwt = Jwt.withTokenValue("t").header("alg", "RS256").subject(studentId.toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        given(currentUserService.getOrCreate(any(Jwt.class))).willReturn(student);
    }

    @Test
    void enrollSnapshotsPriceIncrementsCounterAndRejectsSecondTime() {
        enrollmentService.enroll(course.getId(), studentJwt);

        assertThat(jdbcTemplate.queryForObject("select enrollment_count from courses where id = ?", Integer.class,
                course.getId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select price_paid from enrollments where course_id = ?",
                BigDecimal.class, course.getId())).isEqualByComparingTo("19.99");
        assertThatThrownBy(() -> enrollmentService.enroll(course.getId(), studentJwt))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void continueFollowsSectionThenLessonOrderAndSkipsCompleted() {
        enrollmentService.enroll(course.getId(), studentJwt);

        assertThat(next()).isEqualTo("b1");
        enrollmentService.completeLesson(b1.getId(), studentJwt);
        assertThat(next()).isEqualTo("a1");
        enrollmentService.completeLesson(a2.getId(), studentJwt);
        assertThat(next()).isEqualTo("a1");
    }

    @Test
    void completingIsIdempotentAndKeepsFirstCompletionTime() {
        enrollmentService.enroll(course.getId(), studentJwt);
        enrollmentService.completeLesson(a1.getId(), studentJwt);
        Timestamp first = jdbcTemplate.queryForObject(
                "select completed_at from lesson_progress where lesson_id = ?", Timestamp.class, a1.getId());

        LessonProgressResponse again = enrollmentService.completeLesson(a1.getId(), studentJwt);

        assertThat(jdbcTemplate.queryForObject("select count(*) from lesson_progress where lesson_id = ?",
                Integer.class, a1.getId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select completed_at from lesson_progress where lesson_id = ?",
                Timestamp.class, a1.getId())).isEqualTo(first);
        assertThat(again.completedLessons()).isEqualTo(1);
        assertThat(again.progressPct()).isEqualByComparingTo("33.33");
    }

    @Test
    void completingAllLessonsCompletesCourseAndUncompletingReopensIt() {
        enrollmentService.enroll(course.getId(), studentJwt);
        enrollmentService.completeLesson(a1.getId(), studentJwt);
        enrollmentService.completeLesson(a2.getId(), studentJwt);
        LessonProgressResponse done = enrollmentService.completeLesson(b1.getId(), studentJwt);

        assertThat(done.enrollmentStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
        assertThat(done.progressPct()).isEqualByComparingTo("100");
        ContinueLearningResponse finished = enrollmentService.continueLearning(course.getId(), studentJwt);
        assertThat(finished.courseCompleted()).isTrue();
        assertThat(finished.lesson().title()).isEqualTo("b1");
        assertThat(myCourses("completed")).hasSize(1);

        LessonProgressResponse reopened = enrollmentService.uncompleteLesson(a2.getId(), studentJwt);

        assertThat(reopened.enrollmentStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
        assertThat(reopened.progressPct()).isEqualByComparingTo("66.67");
        assertThat(myCourses("completed")).isEmpty();
        assertThat(myCourses("in-progress")).singleElement()
                .satisfies(row -> assertThat(row.course().title()).isEqualTo("Java"));
    }

    private String next() {
        return enrollmentService.continueLearning(course.getId(), studentJwt).lesson().title();
    }

    private List<MyCourseResponse> myCourses(String status) {
        entityManager.flush();
        entityManager.clear();
        return enrollmentService.findMyCourses(studentJwt, status,
                PageRequest.of(0, 12, Sort.by(Sort.Direction.DESC, "enrolledAt"))).content();
    }

    private Section section(String title, int sortOrder) {
        return entityManager.persist(Section.builder().course(course).title(title).sortOrder(sortOrder).build());
    }

    private Lesson lesson(Section section, String title, int sortOrder) {
        return entityManager.persist(Lesson.builder().section(section).title(title).type(LessonType.TEXT)
                .body("x").sortOrder(sortOrder).build());
    }
}
