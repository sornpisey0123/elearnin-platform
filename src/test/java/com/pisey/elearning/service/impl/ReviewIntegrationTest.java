package com.pisey.elearning.service.impl;

import com.pisey.elearning.config.JpaAuditingConfig;
import com.pisey.elearning.dto.review.ReviewCreateRequest;
import com.pisey.elearning.dto.review.ReviewResponse;
import com.pisey.elearning.dto.review.ReviewUpdateRequest;
import com.pisey.elearning.entity.Category;
import com.pisey.elearning.entity.Course;
import com.pisey.elearning.entity.Enrollment;
import com.pisey.elearning.entity.User;
import com.pisey.elearning.entity.enums.CourseLevel;
import com.pisey.elearning.entity.enums.Role;
import com.pisey.elearning.exception.ConflictException;
import com.pisey.elearning.mapper.ReviewMapperImpl;
import com.pisey.elearning.repository.CategoryRepository;
import com.pisey.elearning.service.CurrentUserService;
import com.pisey.elearning.service.ReviewService;
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
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/** The native rating recalculation (2 dp average, count, zero when empty) and the unique constraint on PostgreSQL. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ReviewServiceImpl.class, CourseAccessServiceImpl.class, ReviewMapperImpl.class, JpaAuditingConfig.class})
@Testcontainers
class ReviewIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private ReviewService reviewService;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private CurrentUserService currentUserService;

    private Course course;
    private User alice;
    private User bob;
    private User carol;

    @BeforeEach
    void setUp() {
        User instructor = entityManager.persist(User.builder().keycloakId(UUID.randomUUID()).role(Role.INSTRUCTOR)
                .email("i@x").fullName("Dara").build());
        Category category = categoryRepository.findBySlug("programming").orElseThrow();
        course = Course.builder().instructor(instructor).category(category).title("Java").slug("java")
                .level(CourseLevel.BEGINNER).price(new BigDecimal("19.99")).build();
        course.publish();
        entityManager.persist(course);
        alice = enrolledStudent("alice");
        bob = enrolledStudent("bob");
        carol = enrolledStudent("carol");
        entityManager.flush();
    }

    @Test
    void averageAndCountFollowCreateUpdateAndDelete() {
        ReviewResponse aliceReview = review(alice, 5);
        review(bob, 4);
        review(carol, 4);
        // (5 + 4 + 4) / 3 = 4.333… → 4.33
        assertRating("4.33", 3);

        reviewService.update(aliceReview.id(), new ReviewUpdateRequest(2, "Changed my mind"), jwt(alice));
        // (2 + 4 + 4) / 3 = 3.33
        assertRating("3.33", 3);

        reviewService.delete(aliceReview.id(), jwt(alice));
        assertRating("4.00", 2);

        reviewService.findByCourse(course.getId(), PageRequest.of(0, 12), null).content()
                .forEach(review -> reviewService.delete(review.id(), jwt(review.author().id().equals(bob.getId()) ? bob : carol)));
        assertRating("0.00", 0);
    }

    @Test
    void listIsNewestFirstWithAuthors() {
        review(alice, 5);
        review(bob, 3);

        var page = reviewService.findByCourse(course.getId(),
                PageRequest.of(0, 12, Sort.by(Sort.Direction.DESC, "createdAt")), null);

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.content()).extracting(review -> review.author().fullName()).containsExactly("bob", "alice");
    }

    @Test
    void secondReviewIs409() {
        review(alice, 5);

        assertThatThrownBy(() -> review(alice, 1)).isInstanceOf(ConflictException.class);
        assertRating("5.00", 1);
    }

    private ReviewResponse review(User student, int rating) {
        given(currentUserService.getOrCreate(any(Jwt.class))).willReturn(student);
        ReviewResponse response = reviewService.create(course.getId(), new ReviewCreateRequest(rating, null), jwt(student));
        // Distinct created_at values so "newest first" is deterministic
        jdbcTemplate.update("update reviews set created_at = created_at + make_interval(secs => id) where id = ?",
                response.id());
        return response;
    }

    private void assertRating(String avgRating, int reviewCount) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select avg_rating, review_count from courses where id = ?", course.getId());
        assertThat((BigDecimal) row.get("avg_rating")).isEqualByComparingTo(avgRating);
        assertThat(row.get("review_count")).isEqualTo(reviewCount);
    }

    private User enrolledStudent(String name) {
        User student = entityManager.persist(User.builder().keycloakId(UUID.randomUUID()).role(Role.STUDENT)
                .email(name + "@x").fullName(name).build());
        entityManager.persist(Enrollment.builder().student(student).course(course).pricePaid(course.getPrice()).build());
        return student;
    }

    private static Jwt jwt(User user) {
        return Jwt.withTokenValue("t").header("alg", "RS256").subject(user.getKeycloakId().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
    }
}
