package com.pisey.elearning.service.impl;

import com.pisey.elearning.config.JpaAuditingConfig;
import com.pisey.elearning.dto.report.AdminOverviewResponse;
import com.pisey.elearning.dto.report.EnrollmentTrendResponse;
import com.pisey.elearning.dto.report.EnrollmentTrendResponse.MonthPoint;
import com.pisey.elearning.dto.report.InstructorCoursesReportResponse;
import com.pisey.elearning.dto.report.InstructorCoursesReportResponse.CourseStats;
import com.pisey.elearning.dto.report.TopCourseResponse;
import com.pisey.elearning.entity.Category;
import com.pisey.elearning.entity.Course;
import com.pisey.elearning.entity.Enrollment;
import com.pisey.elearning.entity.Review;
import com.pisey.elearning.entity.User;
import com.pisey.elearning.entity.enums.CourseLevel;
import com.pisey.elearning.entity.enums.CourseStatus;
import com.pisey.elearning.entity.enums.EnrollmentStatus;
import com.pisey.elearning.entity.enums.Role;
import com.pisey.elearning.exception.ForbiddenException;
import com.pisey.elearning.repository.CategoryRepository;
import com.pisey.elearning.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

/** Every report query on PostgreSQL: grouping, zero-filling, ranking rules and the UTC month series. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ReportServiceImpl.class, CourseAccessServiceImpl.class, JpaAuditingConfig.class})
@Testcontainers
class ReportIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private ReportService reportService;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Category category;
    private User dara;
    private User sokha;

    @BeforeEach
    void setUp() {
        category = categoryRepository.findBySlug("programming").orElseThrow();
        dara = user("dara", Role.INSTRUCTOR);
        sokha = user("sokha", Role.INSTRUCTOR);
    }

    @Test
    void overviewZeroFillsRolesAndSumsRevenue() {
        Course java = course(dara, "Java", CourseStatus.PUBLISHED, "19.99");
        course(dara, "Draft", CourseStatus.DRAFT, "5.00");
        enroll(java, user("s1", Role.STUDENT), "19.99", EnrollmentStatus.ACTIVE, Instant.now());
        enroll(java, user("s2", Role.STUDENT), "10.00", EnrollmentStatus.COMPLETED, Instant.now());

        AdminOverviewResponse overview = reportService.overview();

        assertThat(overview.usersByRole()).containsExactly(
                entry(Role.ADMIN, 0L), entry(Role.INSTRUCTOR, 2L), entry(Role.STUDENT, 2L));
        assertThat(overview.courses()).isEqualTo(new AdminOverviewResponse.CourseCounts(2, 1, 1, 0));
        assertThat(overview.totalEnrollments()).isEqualTo(2);
        assertThat(overview.totalRevenue()).isEqualByComparingTo("29.99");
    }

    @Test
    void overviewOnEmptyPlatformIsAllZeros() {
        entityManager.flush();
        jdbcTemplate.update("delete from users");

        AdminOverviewResponse overview = reportService.overview();

        assertThat(overview.usersByRole()).containsOnlyKeys(Role.values());
        assertThat(overview.usersByRole().values()).containsOnly(0L);
        assertThat(overview.courses().total()).isZero();
        assertThat(overview.totalRevenue()).isEqualByComparingTo("0.00");
        assertThat(overview.totalRevenue().scale()).isEqualTo(2);
    }

    @Test
    void topCoursesByEnrollmentsSkipDraftsAndRespectLimit() {
        Course a = course(dara, "A", CourseStatus.PUBLISHED, "0");
        Course b = course(sokha, "B", CourseStatus.ARCHIVED, "0");
        Course c = course(dara, "C", CourseStatus.PUBLISHED, "0");
        Course draft = course(dara, "D", CourseStatus.DRAFT, "0");
        counters(a, 5, "4.00", 1);
        counters(b, 9, "3.00", 1);
        counters(c, 1, "5.00", 1);
        counters(draft, 50, "0", 0);

        assertThat(reportService.topCourses("enrollments", 10)).extracting(TopCourseResponse::title)
                .containsExactly("B", "A", "C");
        assertThat(reportService.topCourses(null, 2)).extracting(TopCourseResponse::title)
                .containsExactly("B", "A");
        assertThat(reportService.topCourses(null, 1).getFirst().instructorName()).isEqualTo("sokha");
    }

    @Test
    void topCoursesByRatingNeedThreeReviews() {
        Course few = course(dara, "Few reviews", CourseStatus.PUBLISHED, "0");
        Course good = course(dara, "Good", CourseStatus.PUBLISHED, "0");
        Course best = course(sokha, "Best", CourseStatus.PUBLISHED, "0");
        Course tie = course(sokha, "Tie, more reviews", CourseStatus.PUBLISHED, "0");
        counters(few, 0, "5.00", 2);
        counters(good, 0, "4.10", 3);
        counters(best, 0, "4.80", 3);
        counters(tie, 0, "4.80", 7);

        assertThat(reportService.topCourses("RATING", 10)).extracting(TopCourseResponse::title)
                .containsExactly("Tie, more reviews", "Best", "Good");
    }

    @Test
    void instructorReportCoversOnlyOwnCoursesWithTotals() {
        Course java = course(dara, "Java", CourseStatus.PUBLISHED, "20.00");
        Course empty = course(dara, "Empty", CourseStatus.DRAFT, "5.00");
        Course other = course(sokha, "Not mine", CourseStatus.PUBLISHED, "99.00");
        User s1 = user("s1", Role.STUDENT);
        User s2 = user("s2", Role.STUDENT);
        enroll(java, s1, "20.00", EnrollmentStatus.COMPLETED, Instant.now());
        enroll(java, s2, "15.00", EnrollmentStatus.ACTIVE, Instant.now());
        enroll(other, s1, "99.00", EnrollmentStatus.ACTIVE, Instant.now());
        review(java, s1, 5);
        review(java, s2, 4);
        review(other, s1, 1);
        counters(java, 2, "4.50", 2);

        InstructorCoursesReportResponse report = reportService.instructorCourses(jwt(dara));

        assertThat(report.courses()).extracting(CourseStats::courseId)
                .containsExactlyInAnyOrder(java.getId(), empty.getId());
        CourseStats javaStats = report.courses().stream().filter(s -> s.courseId().equals(java.getId())).findFirst()
                .orElseThrow();
        assertThat(javaStats.enrollments()).isEqualTo(2);
        assertThat(javaStats.completedEnrollments()).isEqualTo(1);
        assertThat(javaStats.revenue()).isEqualByComparingTo("35.00");
        CourseStats emptyStats = report.courses().stream().filter(s -> s.courseId().equals(empty.getId())).findFirst()
                .orElseThrow();
        assertThat(emptyStats.enrollments()).isZero();
        assertThat(emptyStats.revenue()).isEqualByComparingTo("0.00");

        assertThat(report.totals().courses()).isEqualTo(2);
        assertThat(report.totals().enrollments()).isEqualTo(2);
        assertThat(report.totals().revenue()).isEqualByComparingTo("35.00");
        // Only dara's reviews (5 and 4); sokha's 1-star review is excluded
        assertThat(report.totals().avgRating()).isEqualByComparingTo("4.50");
        assertThat(report.totals().reviewCount()).isEqualTo(2);
    }

    @Test
    void trendHasEveryMonthOldestFirstWithZeros() {
        Course java = course(dara, "Java", CourseStatus.PUBLISHED, "10.00");
        YearMonth now = YearMonth.now(ZoneOffset.UTC);
        enroll(java, user("s1", Role.STUDENT), "10.00", EnrollmentStatus.ACTIVE, monthStart(now));
        enroll(java, user("s2", Role.STUDENT), "10.00", EnrollmentStatus.ACTIVE, monthStart(now).plusSeconds(60));
        // Last second of the month two months back: must not leak into the following month
        enroll(java, user("s3", Role.STUDENT), "7.50", EnrollmentStatus.ACTIVE, monthStart(now.minusMonths(1)).minusSeconds(1));
        // Older than the window
        enroll(java, user("s4", Role.STUDENT), "10.00", EnrollmentStatus.ACTIVE, monthStart(now.minusMonths(12)));

        EnrollmentTrendResponse trend = reportService.enrollmentTrend(java.getId(), 12, jwt(dara));

        List<String> expectedMonths = IntStream.rangeClosed(0, 11).mapToObj(i -> now.minusMonths(11 - i).toString())
                .toList();
        assertThat(trend.points()).extracting(MonthPoint::month).isEqualTo(expectedMonths);
        assertThat(trend.points()).extracting(MonthPoint::enrollments)
                .containsExactly(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 1L, 0L, 2L);
        assertThat(trend.points().get(9).revenue()).isEqualByComparingTo("7.50");
        assertThat(trend.points().get(11).revenue()).isEqualByComparingTo("20.00");
        assertThat(trend.points().getFirst().revenue()).isEqualByComparingTo("0.00");
    }

    @Test
    void trendLengthFollowsMonthsAndOwnershipIsChecked() {
        Course java = course(dara, "Java", CourseStatus.PUBLISHED, "10.00");

        assertThat(reportService.enrollmentTrend(java.getId(), 1, jwt(dara)).points())
                .singleElement().satisfies(point -> assertThat(point.month())
                        .isEqualTo(YearMonth.now(ZoneOffset.UTC).toString()));
        assertThat(reportService.enrollmentTrend(java.getId(), 24, jwt(dara)).points()).hasSize(24);
        assertThatThrownBy(() -> reportService.enrollmentTrend(java.getId(), 12, jwt(sokha)))
                .isInstanceOf(ForbiddenException.class);
    }

    // --- fixtures ---

    private User user(String name, Role role) {
        return entityManager.persist(User.builder().keycloakId(UUID.randomUUID()).role(role)
                .email(name + "@x").fullName(name).build());
    }

    private Course course(User instructor, String title, CourseStatus status, String price) {
        Course course = Course.builder().instructor(instructor).category(category).title(title)
                .slug(title.toLowerCase().replaceAll("[^a-z]+", "-") + "-" + UUID.randomUUID())
                .level(CourseLevel.BEGINNER).price(new BigDecimal(price)).build();
        if (status != CourseStatus.DRAFT) {
            course.publish();
        }
        if (status == CourseStatus.ARCHIVED) {
            course.archive();
        }
        return entityManager.persist(course);
    }

    private void enroll(Course course, User student, String pricePaid, EnrollmentStatus status, Instant enrolledAt) {
        Enrollment enrollment = Enrollment.builder().student(student).course(course)
                .pricePaid(new BigDecimal(pricePaid)).enrolledAt(enrolledAt).build();
        if (status == EnrollmentStatus.COMPLETED) {
            enrollment.markCompleted();
        }
        entityManager.persistAndFlush(enrollment);
    }

    private void review(Course course, User student, int rating) {
        entityManager.persistAndFlush(Review.builder().course(course).student(student)
                .rating((short) rating).build());
    }

    /** Sets the cached counters the rankings read, as the enrollment and review services would. */
    private void counters(Course course, int enrollments, String avgRating, int reviews) {
        entityManager.flush();
        jdbcTemplate.update("update courses set enrollment_count = ?, avg_rating = ?, review_count = ? where id = ?",
                enrollments, new BigDecimal(avgRating), reviews, course.getId());
    }

    private static Instant monthStart(YearMonth month) {
        return month.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private static Jwt jwt(User user) {
        return Jwt.withTokenValue("t").header("alg", "RS256").subject(user.getKeycloakId().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
    }
}
