package com.pisey.elearning.service.impl;

import com.pisey.elearning.dto.review.ReviewCreateRequest;
import com.pisey.elearning.dto.review.ReviewResponse;
import com.pisey.elearning.dto.review.ReviewUpdateRequest;
import com.pisey.elearning.entity.Category;
import com.pisey.elearning.entity.Course;
import com.pisey.elearning.entity.Review;
import com.pisey.elearning.entity.User;
import com.pisey.elearning.entity.enums.CourseLevel;
import com.pisey.elearning.entity.enums.CourseStatus;
import com.pisey.elearning.entity.enums.Role;
import com.pisey.elearning.exception.ConflictException;
import com.pisey.elearning.exception.ForbiddenException;
import com.pisey.elearning.exception.ResourceNotFoundException;
import com.pisey.elearning.mapper.ReviewMapperImpl;
import com.pisey.elearning.repository.CourseRepository;
import com.pisey.elearning.repository.EnrollmentRepository;
import com.pisey.elearning.repository.ReviewRepository;
import com.pisey.elearning.service.CurrentUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewServiceImplTest {

    private static final UUID STUDENT = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private CurrentUserService currentUserService;

    private ReviewServiceImpl reviewService;
    private Course course;
    private User student;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewServiceImpl(reviewRepository, courseRepository,
                new CourseAccessServiceImpl(enrollmentRepository), currentUserService, new ReviewMapperImpl());

        User instructor = User.builder().keycloakId(UUID.randomUUID()).role(Role.INSTRUCTOR).fullName("Dara")
                .email("d@x").build();
        student = User.builder().keycloakId(STUDENT).role(Role.STUDENT).fullName("Vanna").email("v@x")
                .avatarUrl("/files/avatars/v.png").build();
        student.setId(2L);
        course = Course.builder().instructor(instructor).category(Category.builder().name("P").slug("p").build())
                .title("Java").slug("java").level(CourseLevel.BEGINNER).price(BigDecimal.TEN)
                .status(CourseStatus.PUBLISHED).build();
        course.setId(10L);

        given(courseRepository.findWithInstructorAndCategoryById(10L)).willReturn(Optional.of(course));
        given(currentUserService.getOrCreate(any(Jwt.class))).willReturn(student);
        given(enrollmentRepository.existsByStudentKeycloakIdAndCourseIdAndStatusIn(eq(STUDENT), eq(10L), any()))
                .willReturn(true);
        given(reviewRepository.saveAndFlush(any(Review.class))).willAnswer(invocation -> {
            Review review = invocation.getArgument(0);
            review.setId(90L);
            return review;
        });
        actAs(STUDENT, "STUDENT");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    class Create {

        @Test
        void savesTrimmedReviewAndRecalculatesRating() {
            ReviewResponse response = reviewService.create(10L, new ReviewCreateRequest(4, "  Great course  "),
                    jwt(STUDENT));

            assertThat(response.rating()).isEqualTo(4);
            assertThat(response.comment()).isEqualTo("Great course");
            assertThat(response.courseId()).isEqualTo(10L);
            assertThat(response.author().fullName()).isEqualTo("Vanna");
            assertThat(response.author().avatarUrl()).isEqualTo("/files/avatars/v.png");
            verify(reviewRepository).recalculateCourseRating(10L);
        }

        @Test
        void blankCommentIsStoredAsNull() {
            assertThat(reviewService.create(10L, new ReviewCreateRequest(5, "   "), jwt(STUDENT)).comment()).isNull();
        }

        @Test
        void notEnrolledIs403() {
            given(enrollmentRepository.existsByStudentKeycloakIdAndCourseIdAndStatusIn(eq(STUDENT), eq(10L), any()))
                    .willReturn(false);

            assertThatThrownBy(() -> reviewService.create(10L, new ReviewCreateRequest(5, null), jwt(STUDENT)))
                    .isInstanceOf(ForbiddenException.class);
            verify(reviewRepository, never()).saveAndFlush(any());
        }

        @Test
        void secondReviewIs409() {
            given(reviewRepository.existsByCourseIdAndStudentId(10L, 2L)).willReturn(true);

            assertThatThrownBy(() -> reviewService.create(10L, new ReviewCreateRequest(5, null), jwt(STUDENT)))
                    .isInstanceOf(ConflictException.class);
            verify(reviewRepository, never()).recalculateCourseRating(anyLong());
        }

        @Test
        void concurrentDuplicateIs409() {
            given(reviewRepository.saveAndFlush(any(Review.class)))
                    .willThrow(new DataIntegrityViolationException("uk_reviews_course_id_student_id"));

            assertThatThrownBy(() -> reviewService.create(10L, new ReviewCreateRequest(5, null), jwt(STUDENT)))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void draftCourseIs404() {
            course.setStatus(CourseStatus.DRAFT);

            assertThatThrownBy(() -> reviewService.create(10L, new ReviewCreateRequest(5, null), jwt(STUDENT)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class UpdateAndDelete {

        private Review review;

        @BeforeEach
        void existingReview() {
            review = Review.builder().course(course).student(student).rating((short) 3).comment("ok").build();
            review.setId(90L);
            given(reviewRepository.findWithStudentAndCourseById(90L)).willReturn(Optional.of(review));
        }

        @Test
        void authorUpdatesAndRatingIsRecalculated() {
            ReviewResponse response = reviewService.update(90L, new ReviewUpdateRequest(5, null), jwt(STUDENT));

            assertThat(response.rating()).isEqualTo(5);
            assertThat(response.comment()).isNull();
            verify(reviewRepository).recalculateCourseRating(10L);
        }

        @Test
        void someoneElseUpdatingIs403EvenAdmin() {
            actAs(OTHER, "ADMIN");

            assertThatThrownBy(() -> reviewService.update(90L, new ReviewUpdateRequest(1, null), jwt(OTHER)))
                    .isInstanceOf(ForbiddenException.class);
            assertThat(review.getRating()).isEqualTo((short) 3);
        }

        @Test
        void authorDeletesAndRatingIsRecalculated() {
            reviewService.delete(90L, jwt(STUDENT));

            verify(reviewRepository).delete(review);
            verify(reviewRepository).recalculateCourseRating(10L);
        }

        @Test
        void adminCanDeleteAnyReview() {
            actAs(OTHER, "ADMIN");

            reviewService.delete(90L, jwt(OTHER));

            verify(reviewRepository).delete(review);
        }

        @Test
        void otherStudentDeletingIs403() {
            actAs(OTHER, "STUDENT");

            assertThatThrownBy(() -> reviewService.delete(90L, jwt(OTHER))).isInstanceOf(ForbiddenException.class);
            verify(reviewRepository, never()).delete(any());
        }

        @Test
        void unknownReviewIs404() {
            given(reviewRepository.findWithStudentAndCourseById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> reviewService.delete(99L, jwt(STUDENT)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test
    void findMineWithoutReviewIs404() {
        given(reviewRepository.findByCourseIdAndStudentKeycloakId(10L, STUDENT)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.findMine(10L, jwt(STUDENT)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static void actAs(UUID subject, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(jwt(subject), null, "ROLE_" + role));
    }

    private static Jwt jwt(UUID subject) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(subject.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }
}
