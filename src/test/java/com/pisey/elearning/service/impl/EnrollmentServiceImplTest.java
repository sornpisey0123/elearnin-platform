package com.pisey.elearning.service.impl;

import com.pisey.elearning.dto.enrollment.EnrollmentResponse;
import com.pisey.elearning.dto.enrollment.LessonProgressResponse;
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
import com.pisey.elearning.exception.BadRequestException;
import com.pisey.elearning.exception.ConflictException;
import com.pisey.elearning.exception.ForbiddenException;
import com.pisey.elearning.exception.ResourceNotFoundException;
import com.pisey.elearning.mapper.CourseMapperImpl;
import com.pisey.elearning.mapper.EnrollmentMapperImpl;
import com.pisey.elearning.repository.CourseRepository;
import com.pisey.elearning.repository.EnrollmentRepository;
import com.pisey.elearning.repository.LessonProgressRepository;
import com.pisey.elearning.repository.LessonRepository;
import com.pisey.elearning.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
class EnrollmentServiceImplTest {

    private static final UUID STUDENT = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private LessonProgressRepository lessonProgressRepository;
    @Mock
    private CurrentUserService currentUserService;

    private EnrollmentServiceImpl enrollmentService;
    private User instructor;
    private User student;
    private Course course;
    private Lesson lesson;
    private Enrollment enrollment;

    @BeforeEach
    void setUp() {
        enrollmentService = new EnrollmentServiceImpl(enrollmentRepository, courseRepository, lessonRepository,
                lessonProgressRepository, currentUserService, new EnrollmentMapperImpl(new CourseMapperImpl()));

        instructor = user(1L, UUID.randomUUID(), Role.INSTRUCTOR);
        student = user(2L, STUDENT, Role.STUDENT);
        course = Course.builder().instructor(instructor).category(Category.builder().name("P").slug("p").build())
                .title("Java").slug("java").level(CourseLevel.BEGINNER).price(new BigDecimal("19.99"))
                .status(CourseStatus.PUBLISHED).build();
        course.setId(10L);
        Section section = Section.builder().course(course).title("Intro").sortOrder(1).build();
        section.setId(20L);
        lesson = Lesson.builder().section(section).title("Welcome").type(LessonType.TEXT).sortOrder(1).build();
        lesson.setId(30L);
        enrollment = Enrollment.builder().student(student).course(course).pricePaid(course.getPrice()).build();
        enrollment.setId(40L);

        given(courseRepository.findWithInstructorAndCategoryById(10L)).willReturn(Optional.of(course));
        given(currentUserService.getOrCreate(any(Jwt.class))).willReturn(student);
        given(lessonRepository.findWithCourseById(30L)).willReturn(Optional.of(lesson));
        given(enrollmentRepository.findByStudentKeycloakIdAndCourseIdAndStatusIn(eq(STUDENT), eq(10L), any()))
                .willReturn(Optional.of(enrollment));
    }

    @Nested
    class Enroll {

        @Test
        void snapshotsPriceAndBumpsCounter() {
            given(enrollmentRepository.saveAndFlush(any(Enrollment.class))).willAnswer(invocation -> invocation.getArgument(0));

            EnrollmentResponse response = enrollmentService.enroll(10L, jwt());

            assertThat(response.pricePaid()).isEqualByComparingTo("19.99");
            assertThat(response.status()).isEqualTo(EnrollmentStatus.ACTIVE);
            assertThat(response.courseId()).isEqualTo(10L);
            verify(courseRepository).incrementEnrollmentCount(10L);
        }

        @Test
        void draftCourseIs400() {
            course.setStatus(CourseStatus.DRAFT);

            assertThatThrownBy(() -> enrollmentService.enroll(10L, jwt())).isInstanceOf(BadRequestException.class);
        }

        @Test
        void archivedCourseIs400() {
            course.setStatus(CourseStatus.ARCHIVED);

            assertThatThrownBy(() -> enrollmentService.enroll(10L, jwt())).isInstanceOf(BadRequestException.class);
        }

        @Test
        void ownCourseIs400() {
            given(currentUserService.getOrCreate(any(Jwt.class))).willReturn(instructor);

            assertThatThrownBy(() -> enrollmentService.enroll(10L, jwt()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("own course");
        }

        @Test
        void alreadyEnrolledIs409() {
            given(enrollmentRepository.existsByStudentIdAndCourseId(2L, 10L)).willReturn(true);

            assertThatThrownBy(() -> enrollmentService.enroll(10L, jwt())).isInstanceOf(ConflictException.class);
            verify(enrollmentRepository, never()).saveAndFlush(any());
            verify(courseRepository, never()).incrementEnrollmentCount(anyLong());
        }

        @Test
        void concurrentDuplicateCaughtByConstraintIs409() {
            given(enrollmentRepository.saveAndFlush(any(Enrollment.class)))
                    .willThrow(new DataIntegrityViolationException("uk_enrollments_student_id_course_id"));

            assertThatThrownBy(() -> enrollmentService.enroll(10L, jwt())).isInstanceOf(ConflictException.class);
            verify(courseRepository, never()).incrementEnrollmentCount(anyLong());
        }

        @Test
        void unknownCourseIs404() {
            given(courseRepository.findWithInstructorAndCategoryById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> enrollmentService.enroll(99L, jwt())).isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class Progress {

        @Test
        void completeRecalculatesPercentToTwoDecimals() {
            given(lessonRepository.countBySectionCourseId(10L)).willReturn(3L);
            given(lessonProgressRepository.countByEnrollmentIdAndCompletedTrue(40L)).willReturn(1L);

            LessonProgressResponse response = enrollmentService.completeLesson(30L, jwt());

            verify(lessonProgressRepository).markCompleted(40L, 30L);
            assertThat(response.progressPct()).isEqualByComparingTo("33.33");
            assertThat(response.enrollmentStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
            assertThat(response.completedLessons()).isEqualTo(1);
            assertThat(response.totalLessons()).isEqualTo(3);
        }

        @Test
        void completingLastLessonCompletesEnrollment() {
            given(lessonRepository.countBySectionCourseId(10L)).willReturn(3L);
            given(lessonProgressRepository.countByEnrollmentIdAndCompletedTrue(40L)).willReturn(3L);

            LessonProgressResponse response = enrollmentService.completeLesson(30L, jwt());

            assertThat(response.enrollmentStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
            assertThat(response.progressPct()).isEqualByComparingTo("100");
            assertThat(enrollment.getCompletedAt()).isNotNull();
        }

        @Test
        void uncompleteReopensCompletedEnrollment() {
            enrollment.markCompleted();
            given(lessonRepository.countBySectionCourseId(10L)).willReturn(3L);
            given(lessonProgressRepository.countByEnrollmentIdAndCompletedTrue(40L)).willReturn(2L);

            LessonProgressResponse response = enrollmentService.uncompleteLesson(30L, jwt());

            verify(lessonProgressRepository).markIncomplete(40L, 30L);
            assertThat(response.enrollmentStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
            assertThat(response.progressPct()).isEqualByComparingTo("66.67");
            assertThat(response.lessonCompleted()).isFalse();
            assertThat(enrollment.getCompletedAt()).isNull();
        }

        @Test
        void notEnrolledIs403() {
            given(enrollmentRepository.findByStudentKeycloakIdAndCourseIdAndStatusIn(eq(STUDENT), eq(10L), any()))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> enrollmentService.completeLesson(30L, jwt())).isInstanceOf(ForbiddenException.class);
            verify(lessonProgressRepository, never()).markCompleted(anyLong(), anyLong());
        }

        @Test
        void unknownLessonIs404() {
            given(lessonRepository.findWithCourseById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> enrollmentService.completeLesson(99L, jwt()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class Continue {

        @Test
        void returnsFirstIncompleteLesson() {
            given(lessonRepository.findIncompleteInOrder(eq(10L), eq(40L), any())).willReturn(List.of(lesson));

            var response = enrollmentService.continueLearning(10L, jwt());

            assertThat(response.courseCompleted()).isFalse();
            assertThat(response.lesson().id()).isEqualTo(30L);
            assertThat(response.lesson().sectionTitle()).isEqualTo("Intro");
            verify(lessonRepository).findIncompleteInOrder(10L, 40L, PageRequest.of(0, 1));
        }

        @Test
        void allDoneReturnsCompletedWithFirstLesson() {
            given(lessonRepository.findIncompleteInOrder(eq(10L), eq(40L), any())).willReturn(List.of());
            given(lessonRepository.findInOrder(eq(10L), any())).willReturn(List.of(lesson));

            var response = enrollmentService.continueLearning(10L, jwt());

            assertThat(response.courseCompleted()).isTrue();
            assertThat(response.lesson().id()).isEqualTo(30L);
        }

        @Test
        void notEnrolledIs403() {
            given(enrollmentRepository.findByStudentKeycloakIdAndCourseIdAndStatusIn(eq(STUDENT), eq(10L), any()))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> enrollmentService.continueLearning(10L, jwt()))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    @Test
    void myCoursesRejectsUnknownStatusFilter() {
        assertThatThrownBy(() -> enrollmentService.findMyCourses(jwt(), "paused", PageRequest.of(0, 12)))
                .isInstanceOf(BadRequestException.class);
    }

    private static User user(Long id, UUID keycloakId, Role role) {
        User user = User.builder().keycloakId(keycloakId).role(role).fullName("U" + id).email(id + "@x").build();
        user.setId(id);
        return user;
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue("token").header("alg", "RS256").subject(STUDENT.toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
    }
}
