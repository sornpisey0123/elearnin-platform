package com.pisey.elearning.service.impl;

import com.pisey.elearning.exception.BadRequestException;
import com.pisey.elearning.exception.ResourceNotFoundException;
import com.pisey.elearning.repository.CourseRepository;
import com.pisey.elearning.repository.EnrollmentRepository;
import com.pisey.elearning.repository.ReviewRepository;
import com.pisey.elearning.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** Service-only logic; the queries themselves are covered by {@link ReportIntegrationTest}. */
@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private EnrollmentRepository enrollmentRepositoryForAccess;

    private ReportServiceImpl reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportServiceImpl(userRepository, courseRepository, enrollmentRepository, reviewRepository,
                new CourseAccessServiceImpl(enrollmentRepositoryForAccess));
    }

    @Test
    void unknownRankingIs400() {
        assertThatThrownBy(() -> reportService.topCourses("price", 10))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("enrollments or rating");
        verifyNoInteractions(courseRepository);
    }

    @Test
    void rankingNameIsCaseInsensitive() {
        given(courseRepository.findTopByRating(any(), eq(3), any())).willReturn(List.of());

        assertThat(reportService.topCourses(" Rating ", 5)).isEmpty();
        verify(courseRepository).findTopByRating(any(), eq(3), any());
    }

    @Test
    void instructorWithoutCoursesGetsZeroTotals() {
        given(courseRepository.findInstructorCourseStats(any(), any())).willReturn(List.of());
        given(reviewRepository.findAverageRatingByInstructor(any())).willReturn(null);

        var report = reportService.instructorCourses(jwt());

        assertThat(report.courses()).isEmpty();
        assertThat(report.totals().courses()).isZero();
        assertThat(report.totals().revenue()).isEqualByComparingTo("0.00");
        assertThat(report.totals().avgRating()).isEqualByComparingTo("0.00");
    }

    @Test
    void trendOfUnknownCourseIs404() {
        given(courseRepository.findWithInstructorAndCategoryById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.enrollmentTrend(99L, 12, jwt()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(enrollmentRepository, never()).findMonthlyTrend(any(), anyInt());
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue("token").header("alg", "RS256").subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
    }
}
