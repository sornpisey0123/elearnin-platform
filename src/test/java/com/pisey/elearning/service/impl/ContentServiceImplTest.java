package com.pisey.elearning.service.impl;

import com.pisey.elearning.dto.content.CourseOutlineResponse;
import com.pisey.elearning.dto.content.LessonCreateRequest;
import com.pisey.elearning.dto.content.LessonResponse;
import com.pisey.elearning.dto.content.LessonUpdateRequest;
import com.pisey.elearning.dto.content.SectionCreateRequest;
import com.pisey.elearning.entity.Category;
import com.pisey.elearning.entity.Course;
import com.pisey.elearning.entity.Lesson;
import com.pisey.elearning.entity.Section;
import com.pisey.elearning.entity.User;
import com.pisey.elearning.entity.enums.CourseLevel;
import com.pisey.elearning.entity.enums.CourseStatus;
import com.pisey.elearning.entity.enums.LessonType;
import com.pisey.elearning.entity.enums.Role;
import com.pisey.elearning.exception.BadRequestException;
import com.pisey.elearning.exception.ForbiddenException;
import com.pisey.elearning.exception.ResourceNotFoundException;
import com.pisey.elearning.mapper.ContentMapperImpl;
import com.pisey.elearning.repository.CourseRepository;
import com.pisey.elearning.repository.EnrollmentRepository;
import com.pisey.elearning.repository.LessonRepository;
import com.pisey.elearning.repository.LessonResourceRepository;
import com.pisey.elearning.repository.QuizRepository;
import com.pisey.elearning.repository.SectionRepository;
import com.pisey.elearning.service.FileStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContentServiceImplTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID STUDENT = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    @Mock
    private CourseRepository courseRepository;
    @Mock
    private SectionRepository sectionRepository;
    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private LessonResourceRepository lessonResourceRepository;
    @Mock
    private QuizRepository quizRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private FileStorageService fileStorageService;

    private ContentServiceImpl contentService;
    private Course course;
    private Section section;

    @BeforeEach
    void setUp() {
        contentService = new ContentServiceImpl(courseRepository, sectionRepository, lessonRepository,
                lessonResourceRepository, quizRepository, new CourseAccessServiceImpl(enrollmentRepository),
                fileStorageService, new ContentMapperImpl());

        User owner = User.builder().keycloakId(OWNER).role(Role.INSTRUCTOR).fullName("Dara").email("d@x").build();
        Category category = Category.builder().name("Programming").slug("programming").build();
        course = Course.builder().instructor(owner).category(category).title("Java").slug("java")
                .level(CourseLevel.BEGINNER).price(BigDecimal.TEN).status(CourseStatus.PUBLISHED).build();
        course.setId(10L);
        section = Section.builder().course(course).title("Intro").sortOrder(1).build();
        section.setId(20L);
        course.getSections().add(section);
        given(courseRepository.findWithInstructorAndCategoryById(10L)).willReturn(Optional.of(course));
        given(sectionRepository.findWithCourseById(20L)).willReturn(Optional.of(section));
        actAs(OWNER, "INSTRUCTOR");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    class Outline {

        @Test
        void listsLessonsWithQuizFlag() {
            Lesson video = lesson(30L, LessonType.VIDEO, false);
            Lesson text = lesson(31L, LessonType.TEXT, false);
            given(sectionRepository.findOutline(10L)).willReturn(List.of(section));
            given(quizRepository.findLessonIdsWithQuizByCourseId(10L)).willReturn(List.of(31L));

            CourseOutlineResponse outline = contentService.getOutline(10L, null);

            assertThat(outline.sections()).hasSize(1);
            assertThat(outline.sections().getFirst().lessons())
                    .extracting(CourseOutlineResponse.LessonOutline::id, CourseOutlineResponse.LessonOutline::hasQuiz)
                    .containsExactly(tuple(30L, false),
                            tuple(31L, true));
        }

        @Test
        void draftCourseOutlineIs404ForPublic() {
            course.setStatus(CourseStatus.DRAFT);

            assertThatThrownBy(() -> contentService.getOutline(10L, null)).isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class LessonAccess {

        private Lesson lesson;

        @BeforeEach
        void setUpLesson() {
            lesson = lesson(30L, LessonType.VIDEO, false);
            given(lessonRepository.findWithCourseById(30L)).willReturn(Optional.of(lesson));
            actAs(STUDENT, "STUDENT");
        }

        @Test
        void notEnrolledStudentGets403WithEnrollMessage() {
            assertThatThrownBy(() -> contentService.getLesson(30L, jwt(STUDENT)))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Enroll to access this lesson");
        }

        @Test
        void enrolledStudentGetsFullContent() {
            given(enrollmentRepository.existsByStudentKeycloakIdAndCourseIdAndStatusIn(eq(STUDENT), eq(10L), any()))
                    .willReturn(true);

            LessonResponse response = contentService.getLesson(30L, jwt(STUDENT));

            assertThat(response.videoUrl()).isEqualTo("https://video/30");
            assertThat(response.courseId()).isEqualTo(10L);
        }

        @Test
        void previewLessonOfPublishedCourseIsOpen() {
            lesson.setPreview(true);

            assertThat(contentService.getLesson(30L, jwt(STUDENT)).videoUrl()).isEqualTo("https://video/30");
        }

        @Test
        void previewLessonOfDraftCourseIsHidden() {
            lesson.setPreview(true);
            course.setStatus(CourseStatus.DRAFT);

            assertThatThrownBy(() -> contentService.getLesson(30L, jwt(STUDENT)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void archivedCourseIsOpenToEnrolledOnly() {
            course.setStatus(CourseStatus.ARCHIVED);
            assertThatThrownBy(() -> contentService.getLesson(30L, jwt(STUDENT)))
                    .isInstanceOf(ResourceNotFoundException.class);

            given(enrollmentRepository.existsByStudentKeycloakIdAndCourseIdAndStatusIn(eq(STUDENT), eq(10L), any()))
                    .willReturn(true);
            assertThat(contentService.getLesson(30L, jwt(STUDENT)).id()).isEqualTo(30L);
        }

        @Test
        void ownerAlwaysHasAccess() {
            course.setStatus(CourseStatus.DRAFT);
            actAs(OWNER, "INSTRUCTOR");

            assertThat(contentService.getLesson(30L, jwt(OWNER)).id()).isEqualTo(30L);
        }

        @Test
        void unknownLessonIs404() {
            given(lessonRepository.findWithCourseById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> contentService.getLesson(99L, jwt(STUDENT)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class Writes {

        @Test
        void sectionIsAppendedAfterLast() {
            given(sectionRepository.findMaxSortOrder(10L)).willReturn(3);
            given(sectionRepository.save(any(Section.class))).willAnswer(invocation -> invocation.getArgument(0));

            assertThat(contentService.createSection(10L, new SectionCreateRequest(" Advanced "), jwt(OWNER)).sortOrder())
                    .isEqualTo(4);
        }

        @Test
        void otherInstructorCannotAddSection() {
            actAs(OTHER, "INSTRUCTOR");

            assertThatThrownBy(() -> contentService.createSection(10L, new SectionCreateRequest("X"), jwt(OTHER)))
                    .isInstanceOf(ForbiddenException.class);
            verify(sectionRepository, never()).save(any());
        }

        @Test
        void videoLessonNeedsUrlAndDuration() {
            assertThatThrownBy(() -> contentService.createLesson(20L,
                    new LessonCreateRequest("Intro", LessonType.VIDEO, "https://v/1", null, null, null), jwt(OWNER)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("durationSeconds");
        }

        @Test
        void textLessonNeedsBody() {
            assertThatThrownBy(() -> contentService.createLesson(20L,
                    new LessonCreateRequest("Notes", LessonType.TEXT, null, null, " ", null), jwt(OWNER)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("body");
        }

        @Test
        void lessonKeepsOnlyFieldsOfItsTypeAndIsAppended() {
            given(lessonRepository.findMaxSortOrder(20L)).willReturn(2);
            given(lessonRepository.save(any(Lesson.class))).willAnswer(invocation -> invocation.getArgument(0));

            LessonResponse response = contentService.createLesson(20L,
                    new LessonCreateRequest("Notes", LessonType.TEXT, "https://ignored", 99, "Hello", true), jwt(OWNER));

            assertThat(response.body()).isEqualTo("Hello");
            assertThat(response.videoUrl()).isNull();
            assertThat(response.durationSeconds()).isNull();
            assertThat(response.sortOrder()).isEqualTo(3);
            assertThat(response.preview()).isTrue();
        }

        @Test
        void changingAwayFromFileDeletesUploadedFileAfterCommit() {
            TransactionSynchronizationManager.initSynchronization();
            try {
                Lesson lesson = lesson(30L, LessonType.FILE, false);
                lesson.setFileUrl("/files/lessons/old.pdf");
                given(lessonRepository.findWithCourseById(30L)).willReturn(Optional.of(lesson));
                given(lessonRepository.saveAndFlush(lesson)).willReturn(lesson);

                LessonResponse response = contentService.updateLesson(30L,
                        new LessonUpdateRequest("Now text", LessonType.TEXT, null, null, "Body", false), jwt(OWNER));

                assertThat(response.fileUrl()).isNull();
                TransactionSynchronizationManager.getSynchronizations()
                        .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
                verify(fileStorageService).delete("/files/lessons/old.pdf");
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }

        @Test
        void fileUploadOnlyForFileLessons() {
            given(lessonRepository.findWithCourseById(30L)).willReturn(Optional.of(lesson(30L, LessonType.VIDEO, false)));

            assertThatThrownBy(() -> contentService.uploadLessonFile(30L,
                    new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[]{1}), jwt(OWNER)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("FILE");
            verify(fileStorageService, never()).store(any(), any(), any(), anyLong());
        }
    }

    @Nested
    class Reorder {

        private Section second;
        private Section third;

        @BeforeEach
        void setUpSections() {
            second = Section.builder().course(course).title("Two").sortOrder(2).build();
            second.setId(21L);
            third = Section.builder().course(course).title("Three").sortOrder(3).build();
            third.setId(22L);
            given(sectionRepository.findByCourseIdOrderBySortOrder(10L)).willReturn(List.of(section, second, third));
        }

        @Test
        void appliesRequestedOrder() {
            contentService.reorderSections(10L, List.of(22L, 20L, 21L), jwt(OWNER));

            assertThat(third.getSortOrder()).isEqualTo(1);
            assertThat(section.getSortOrder()).isEqualTo(2);
            assertThat(second.getSortOrder()).isEqualTo(3);
        }

        @Test
        void rejectsMissingExtraOrDuplicateIds() {
            for (List<Long> ids : List.of(List.of(20L, 21L), List.of(20L, 21L, 22L, 99L), List.of(20L, 21L, 21L))) {
                assertThatThrownBy(() -> contentService.reorderSections(10L, ids, jwt(OWNER)))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessageContaining("exactly once");
            }
            assertThat(section.getSortOrder()).isEqualTo(1);
        }
    }

    private Lesson lesson(Long id, LessonType type, boolean preview) {
        Lesson lesson = Lesson.builder()
                .title("Lesson " + id)
                .type(type)
                .videoUrl(type == LessonType.VIDEO ? "https://video/" + id : null)
                .durationSeconds(type == LessonType.VIDEO ? 300 : null)
                .body(type == LessonType.TEXT ? "text" : null)
                .sortOrder(section.getLessons().size() + 1)
                .preview(preview)
                .build();
        lesson.setId(id);
        section.addLesson(lesson);
        return lesson;
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
