package com.pisey.elearning.service.impl;

import com.pisey.elearning.dto.course.CourseCreateRequest;
import com.pisey.elearning.dto.course.CourseDetailResponse;
import com.pisey.elearning.dto.course.CourseSearchCriteria;
import com.pisey.elearning.dto.course.CourseSearchCriteria.CourseSort;
import com.pisey.elearning.dto.course.CourseUpdateRequest;
import com.pisey.elearning.entity.Category;
import com.pisey.elearning.entity.Course;
import com.pisey.elearning.entity.User;
import com.pisey.elearning.entity.enums.CourseLevel;
import com.pisey.elearning.entity.enums.CourseStatus;
import com.pisey.elearning.entity.enums.EnrollmentStatus;
import com.pisey.elearning.entity.enums.Role;
import com.pisey.elearning.exception.BadRequestException;
import com.pisey.elearning.exception.ConflictException;
import com.pisey.elearning.exception.ForbiddenException;
import com.pisey.elearning.exception.ResourceNotFoundException;
import com.pisey.elearning.mapper.CourseMapperImpl;
import com.pisey.elearning.repository.CategoryRepository;
import com.pisey.elearning.repository.CourseRepository;
import com.pisey.elearning.repository.EnrollmentRepository;
import com.pisey.elearning.repository.LessonRepository;
import com.pisey.elearning.repository.LessonResourceRepository;
import com.pisey.elearning.repository.projection.CourseContentStats;
import com.pisey.elearning.service.CurrentUserService;
import com.pisey.elearning.service.FileStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourseServiceImplTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID OTHER_INSTRUCTOR = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID STUDENT = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final Stats EMPTY = new Stats(0, 0, 0);

    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private LessonResourceRepository lessonResourceRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private FileStorageService fileStorageService;

    private CourseServiceImpl courseService;
    private User owner;
    private Category programming;

    @BeforeEach
    void setUp() {
        courseService = new CourseServiceImpl(courseRepository, categoryRepository, enrollmentRepository,
                lessonRepository, lessonResourceRepository, new CourseAccessServiceImpl(enrollmentRepository),
                currentUserService, fileStorageService, new CourseMapperImpl());
        owner = User.builder().keycloakId(OWNER).role(Role.INSTRUCTOR).fullName("Dara Chan").email("i1@x").build();
        owner.setId(2L);
        programming = Category.builder().name("Programming").slug("programming").build();
        programming.setId(1L);
        given(courseRepository.findContentStats(anyLong())).willReturn(EMPTY);
        actAs(OWNER, "INSTRUCTOR");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    class Search {

        @Test
        @SuppressWarnings("unchecked")
        void clampsPageSizeAndAppliesNamedSort() {
            given(courseRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .willAnswer(invocation -> new PageImpl<Course>(List.of(), invocation.getArgument(1), 0));

            courseService.search(new CourseSearchCriteria(null, null, null, null, null), CourseSort.RATING, -3, 500);

            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(courseRepository).findAll(any(Specification.class), pageable.capture());
            assertThat(pageable.getValue().getPageNumber()).isZero();
            assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
            assertThat(pageable.getValue().getSort().getOrderFor("avgRating").getDirection()).isEqualTo(Sort.Direction.DESC);
        }

        @Test
        void rejectsMinPriceAboveMaxPrice() {
            assertThatThrownBy(() -> courseService.search(
                    new CourseSearchCriteria(null, null, null, new BigDecimal("50"), new BigDecimal("10")),
                    CourseSort.NEWEST, 0, 12))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void sortOptionParsingIsCaseInsensitiveAndDefaultsToNewest() {
            assertThat(CourseSort.from(null)).isEqualTo(CourseSort.NEWEST);
            assertThat(CourseSort.from("Popular")).isEqualTo(CourseSort.POPULAR);
            assertThatThrownBy(() -> CourseSort.from("cheapest")).isInstanceOf(BadRequestException.class);
        }
    }

    @Nested
    class Visibility {

        @Test
        void publishedCourseIsVisibleWithoutToken() {
            Course course = course(10L, CourseStatus.PUBLISHED);
            given(courseRepository.findBySlug("java")).willReturn(Optional.of(course));

            assertThat(courseService.findBySlug("java", null).id()).isEqualTo(10L);
        }

        @Test
        void draftIsHiddenFromAnonymousAndOtherUsers() {
            given(courseRepository.findBySlug("java")).willReturn(Optional.of(course(10L, CourseStatus.DRAFT)));

            assertThatThrownBy(() -> courseService.findBySlug("java", null))
                    .isInstanceOf(ResourceNotFoundException.class);
            actAs(STUDENT, "STUDENT");
            assertThatThrownBy(() -> courseService.findBySlug("java", jwt(STUDENT)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void draftIsVisibleToOwnerAndAdmin() {
            given(courseRepository.findBySlug("java")).willReturn(Optional.of(course(10L, CourseStatus.DRAFT)));

            assertThat(courseService.findBySlug("java", jwt(OWNER)).status()).isEqualTo(CourseStatus.DRAFT);
            actAs(ADMIN, "ADMIN");
            assertThat(courseService.findBySlug("java", jwt(ADMIN)).status()).isEqualTo(CourseStatus.DRAFT);
        }

        @Test
        void archivedIsVisibleToEnrolledStudentOnly() {
            given(courseRepository.findBySlug("java")).willReturn(Optional.of(course(10L, CourseStatus.ARCHIVED)));
            given(enrollmentRepository.existsByStudentKeycloakIdAndCourseIdAndStatusIn(eq(STUDENT), eq(10L), any()))
                    .willReturn(true);
            actAs(STUDENT, "STUDENT");

            assertThat(courseService.findBySlug("java", jwt(STUDENT)).status()).isEqualTo(CourseStatus.ARCHIVED);

            UUID stranger = UUID.randomUUID();
            actAs(stranger, "STUDENT");
            assertThatThrownBy(() -> courseService.findBySlug("java", jwt(stranger)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void detailIncludesContentStats() {
            given(courseRepository.findBySlug("java")).willReturn(Optional.of(course(10L, CourseStatus.PUBLISHED)));
            given(courseRepository.findContentStats(10L)).willReturn(new Stats(2, 5, 3600));

            CourseDetailResponse detail = courseService.findBySlug("java", null);

            assertThat(detail.sectionCount()).isEqualTo(2);
            assertThat(detail.lessonCount()).isEqualTo(5);
            assertThat(detail.totalDurationSeconds()).isEqualTo(3600);
            assertThat(detail.instructor().fullName()).isEqualTo("Dara Chan");
            assertThat(detail.category().slug()).isEqualTo("programming");
        }
    }

    @Nested
    class CreateAndUpdate {

        @Test
        void createIsDraftWithGeneratedSlugAndCallerAsInstructor() {
            given(currentUserService.getOrCreate(any(Jwt.class))).willReturn(owner);
            given(categoryRepository.findById(1L)).willReturn(Optional.of(programming));
            given(courseRepository.existsBySlug("java-for-beginners")).willReturn(true);
            given(courseRepository.save(any(Course.class))).willAnswer(invocation -> invocation.getArgument(0));

            CourseDetailResponse detail = courseService.create(jwt(OWNER), new CourseCreateRequest(
                    " Java for Beginners ", "Learn Java", 1L, CourseLevel.BEGINNER, new BigDecimal("19.99")));

            assertThat(detail.status()).isEqualTo(CourseStatus.DRAFT);
            assertThat(detail.slug()).isEqualTo("java-for-beginners-2");
            assertThat(detail.title()).isEqualTo("Java for Beginners");
            assertThat(detail.instructor().id()).isEqualTo(2L);
        }

        @Test
        void createWithUnknownCategoryIs400() {
            given(currentUserService.getOrCreate(any(Jwt.class))).willReturn(owner);
            given(categoryRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> courseService.create(jwt(OWNER), new CourseCreateRequest(
                    "Java", null, 99L, CourseLevel.BEGINNER, BigDecimal.ZERO)))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void updateDraftTitleRegeneratesSlug() {
            Course course = course(10L, CourseStatus.DRAFT);
            given(courseRepository.findWithInstructorAndCategoryById(10L)).willReturn(Optional.of(course));
            given(courseRepository.saveAndFlush(course)).willReturn(course);

            CourseDetailResponse detail = courseService.update(10L, updateRequest("Spring Boot 4"), jwt(OWNER));

            assertThat(detail.slug()).isEqualTo("spring-boot-4");
        }

        @Test
        void updateOfOncePublishedCourseKeepsSlug() {
            Course course = course(10L, CourseStatus.DRAFT);
            course.setPublishedAt(Instant.now().minusSeconds(3600));
            given(courseRepository.findWithInstructorAndCategoryById(10L)).willReturn(Optional.of(course));
            given(courseRepository.saveAndFlush(course)).willReturn(course);

            CourseDetailResponse detail = courseService.update(10L, updateRequest("Spring Boot 4"), jwt(OWNER));

            assertThat(detail.slug()).isEqualTo("java");
            assertThat(detail.title()).isEqualTo("Spring Boot 4");
            verify(courseRepository, never()).existsBySlugAndIdNot(anyString(), anyLong());
        }

        @Test
        void otherInstructorGets403() {
            given(courseRepository.findWithInstructorAndCategoryById(10L))
                    .willReturn(Optional.of(course(10L, CourseStatus.DRAFT)));
            actAs(OTHER_INSTRUCTOR, "INSTRUCTOR");

            assertThatThrownBy(() -> courseService.update(10L, updateRequest("Hijack"), jwt(OTHER_INSTRUCTOR)))
                    .isInstanceOf(ForbiddenException.class);
            verify(courseRepository, never()).saveAndFlush(any());
        }

        @Test
        void adminBypassesOwnership() {
            Course course = course(10L, CourseStatus.DRAFT);
            given(courseRepository.findWithInstructorAndCategoryById(10L)).willReturn(Optional.of(course));
            given(courseRepository.saveAndFlush(course)).willReturn(course);
            actAs(ADMIN, "ADMIN");

            assertThat(courseService.update(10L, updateRequest("Fixed Title"), jwt(ADMIN)).title())
                    .isEqualTo("Fixed Title");
        }

        @Test
        void unknownCourseIs404() {
            given(courseRepository.findWithInstructorAndCategoryById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> courseService.update(99L, updateRequest("X"), jwt(OWNER)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        private CourseUpdateRequest updateRequest(String title) {
            return new CourseUpdateRequest(title, null, 1L, CourseLevel.INTERMEDIATE, BigDecimal.TEN);
        }
    }

    @Nested
    class Thumbnail {

        @BeforeEach
        void startSynchronization() {
            TransactionSynchronizationManager.initSynchronization();
        }

        @AfterEach
        void clearSynchronization() {
            TransactionSynchronizationManager.clearSynchronization();
        }

        @Test
        void storesImageUpTo5MbAndDeletesOldFileAfterCommit() {
            Course course = course(10L, CourseStatus.DRAFT);
            course.setThumbnailUrl("/files/thumbnails/old.png");
            MockMultipartFile file = new MockMultipartFile("file", "t.png", "image/png", new byte[]{1});
            given(courseRepository.findWithInstructorAndCategoryById(10L)).willReturn(Optional.of(course));
            given(fileStorageService.store(file, "thumbnails", CourseServiceImpl.THUMBNAIL_TYPES, 5L * 1024 * 1024))
                    .willReturn("/files/thumbnails/new.png");

            assertThat(courseService.updateThumbnail(10L, file, jwt(OWNER)).thumbnailUrl())
                    .isEqualTo("/files/thumbnails/new.png");
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

            verify(fileStorageService).delete("/files/thumbnails/old.png");
        }
    }

    @Nested
    class PublishLifecycle {

        @Test
        void publishWithoutSectionsIs400() {
            given(courseRepository.findWithInstructorAndCategoryById(10L))
                    .willReturn(Optional.of(course(10L, CourseStatus.DRAFT)));

            assertThatThrownBy(() -> courseService.publish(10L, jwt(OWNER)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("section");
        }

        @Test
        void publishWithEmptySectionIs400() {
            given(courseRepository.findWithInstructorAndCategoryById(10L))
                    .willReturn(Optional.of(course(10L, CourseStatus.DRAFT)));
            given(courseRepository.findContentStats(10L)).willReturn(new Stats(1, 0, 0));

            assertThatThrownBy(() -> courseService.publish(10L, jwt(OWNER)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("lesson");
        }

        @Test
        void publishWithLessonSetsPublishedAt() {
            Course course = course(10L, CourseStatus.DRAFT);
            given(courseRepository.findWithInstructorAndCategoryById(10L)).willReturn(Optional.of(course));
            given(courseRepository.findContentStats(10L)).willReturn(new Stats(1, 1, 300));

            CourseDetailResponse detail = courseService.publish(10L, jwt(OWNER));

            assertThat(detail.status()).isEqualTo(CourseStatus.PUBLISHED);
            assertThat(detail.publishedAt()).isNotNull();
            verify(courseRepository).saveAndFlush(course);
        }

        @Test
        void unpublishWithActiveEnrollmentsIs409() {
            given(courseRepository.findWithInstructorAndCategoryById(10L))
                    .willReturn(Optional.of(course(10L, CourseStatus.PUBLISHED)));
            given(enrollmentRepository.existsByCourseIdAndStatus(10L, EnrollmentStatus.ACTIVE)).willReturn(true);

            assertThatThrownBy(() -> courseService.unpublish(10L, jwt(OWNER))).isInstanceOf(ConflictException.class);
        }

        @Test
        void unpublishWithoutActiveEnrollmentsReturnsToDraft() {
            Course course = course(10L, CourseStatus.PUBLISHED);
            given(courseRepository.findWithInstructorAndCategoryById(10L)).willReturn(Optional.of(course));
            given(courseRepository.saveAndFlush(course)).willReturn(course);

            assertThat(courseService.unpublish(10L, jwt(OWNER)).status()).isEqualTo(CourseStatus.DRAFT);
        }
    }

    @Nested
    class Delete {

        @BeforeEach
        void startSynchronization() {
            TransactionSynchronizationManager.initSynchronization();
        }

        @AfterEach
        void clearSynchronization() {
            TransactionSynchronizationManager.clearSynchronization();
        }

        @Test
        void courseWithEnrollmentsIsArchivedNotDeleted() {
            Course course = course(10L, CourseStatus.PUBLISHED);
            given(courseRepository.findWithInstructorAndCategoryById(10L)).willReturn(Optional.of(course));
            given(enrollmentRepository.existsByCourseId(10L)).willReturn(true);

            assertThat(courseService.delete(10L, jwt(OWNER))).isTrue();

            assertThat(course.getStatus()).isEqualTo(CourseStatus.ARCHIVED);
            assertThat(course.getArchivedAt()).isNotNull();
            verify(courseRepository, never()).delete(any(Course.class));
        }

        @Test
        void courseWithoutEnrollmentsIsDeletedWithAllItsFiles() {
            Course course = course(10L, CourseStatus.DRAFT);
            course.setThumbnailUrl("/files/thumbnails/t.png");
            given(courseRepository.findWithInstructorAndCategoryById(10L)).willReturn(Optional.of(course));
            given(lessonRepository.findFileUrlsByCourseId(10L)).willReturn(List.of("/files/lessons/l.pdf"));
            given(lessonResourceRepository.findFileUrlsByCourseId(10L)).willReturn(List.of("/files/resources/r.zip"));

            assertThat(courseService.delete(10L, jwt(OWNER))).isFalse();
            verify(courseRepository).delete(course);
            verify(fileStorageService, never()).delete(anyString());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
            verify(fileStorageService).delete("/files/thumbnails/t.png");
            verify(fileStorageService).delete("/files/lessons/l.pdf");
            verify(fileStorageService).delete("/files/resources/r.zip");
        }

        @Test
        void otherInstructorCannotDelete() {
            given(courseRepository.findWithInstructorAndCategoryById(10L))
                    .willReturn(Optional.of(course(10L, CourseStatus.DRAFT)));
            actAs(OTHER_INSTRUCTOR, "INSTRUCTOR");

            assertThatThrownBy(() -> courseService.delete(10L, jwt(OTHER_INSTRUCTOR)))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    private Course course(Long id, CourseStatus status) {
        Course course = Course.builder()
                .instructor(owner)
                .category(programming)
                .title("Java")
                .slug("java")
                .level(CourseLevel.BEGINNER)
                .price(BigDecimal.TEN)
                .status(status)
                .build();
        course.setId(id);
        return course;
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

    private record Stats(long sectionCount, long lessonCount, long totalDurationSeconds) implements CourseContentStats {

        @Override
        public long getSectionCount() {
            return sectionCount;
        }

        @Override
        public long getLessonCount() {
            return lessonCount;
        }

        @Override
        public long getTotalDurationSeconds() {
            return totalDurationSeconds;
        }
    }
}
