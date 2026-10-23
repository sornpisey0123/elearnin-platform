package com.pisey.elearning.service.impl;

import com.pisey.elearning.dto.common.PageResponse;
import com.pisey.elearning.dto.course.CourseCreateRequest;
import com.pisey.elearning.dto.course.CourseDetailResponse;
import com.pisey.elearning.dto.course.CourseSearchCriteria;
import com.pisey.elearning.dto.course.CourseSearchCriteria.CourseSort;
import com.pisey.elearning.dto.course.CourseSummaryResponse;
import com.pisey.elearning.dto.course.CourseUpdateRequest;
import com.pisey.elearning.dto.course.InstructorCourseResponse;
import com.pisey.elearning.entity.Category;
import com.pisey.elearning.entity.Course;
import com.pisey.elearning.entity.enums.CourseStatus;
import com.pisey.elearning.entity.enums.EnrollmentStatus;
import com.pisey.elearning.exception.BadRequestException;
import com.pisey.elearning.exception.ConflictException;
import com.pisey.elearning.exception.ResourceNotFoundException;
import com.pisey.elearning.mapper.CourseMapper;
import com.pisey.elearning.repository.CategoryRepository;
import com.pisey.elearning.repository.CourseRepository;
import com.pisey.elearning.repository.EnrollmentRepository;
import com.pisey.elearning.repository.LessonRepository;
import com.pisey.elearning.repository.LessonResourceRepository;
import com.pisey.elearning.repository.projection.CourseContentStats;
import com.pisey.elearning.service.CourseAccessService;
import com.pisey.elearning.service.CourseService;
import com.pisey.elearning.service.CurrentUserService;
import com.pisey.elearning.service.FileStorageService;
import com.pisey.elearning.utils.FileTransactionUtils;
import com.pisey.elearning.utils.SecurityUtils;
import com.pisey.elearning.utils.SlugUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.pisey.elearning.repository.specification.CourseSpecifications.hasLevel;
import static com.pisey.elearning.repository.specification.CourseSpecifications.hasStatus;
import static com.pisey.elearning.repository.specification.CourseSpecifications.inCategory;
import static com.pisey.elearning.repository.specification.CourseSpecifications.keywordMatches;
import static com.pisey.elearning.repository.specification.CourseSpecifications.ownedBy;
import static com.pisey.elearning.repository.specification.CourseSpecifications.priceAtLeast;
import static com.pisey.elearning.repository.specification.CourseSpecifications.priceAtMost;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseServiceImpl implements CourseService {

    static final int MAX_PAGE_SIZE = 50;
    static final String THUMBNAIL_FOLDER = "thumbnails";
    static final Set<String> THUMBNAIL_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    static final long THUMBNAIL_MAX_BYTES = 5L * 1024 * 1024;

    private static final int SLUG_MAX_LENGTH = 220;

    private final CourseRepository courseRepository;
    private final CategoryRepository categoryRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final LessonRepository lessonRepository;
    private final LessonResourceRepository lessonResourceRepository;
    private final CourseAccessService courseAccessService;
    private final CurrentUserService currentUserService;
    private final FileStorageService fileStorageService;
    private final CourseMapper courseMapper;

    @Override
    public PageResponse<CourseSummaryResponse> search(CourseSearchCriteria criteria, CourseSort sort, int page, int size) {
        if (criteria.minPrice() != null && criteria.maxPrice() != null
                && criteria.minPrice().compareTo(criteria.maxPrice()) > 0) {
            throw new BadRequestException("minPrice must not be greater than maxPrice");
        }
        Specification<Course> spec = Specification.allOf(
                hasStatus(CourseStatus.PUBLISHED),
                keywordMatches(criteria.keyword()),
                inCategory(criteria.categoryId()),
                hasLevel(criteria.level()),
                priceAtLeast(criteria.minPrice()),
                priceAtMost(criteria.maxPrice()));
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), sort.sort());
        return PageResponse.from(courseRepository.findAll(spec, pageable), courseMapper::toSummaryResponse);
    }

    @Override
    public CourseDetailResponse findBySlug(String slug, Jwt caller) {
        Course course = courseRepository.findBySlug(slug)
                .filter(found -> courseAccessService.isVisible(found, caller))
                .orElseThrow(() -> new ResourceNotFoundException("Course", "slug", slug));
        return toDetail(course);
    }

    @Override
    public PageResponse<InstructorCourseResponse> findOwn(Jwt caller, CourseStatus status, Pageable pageable) {
        Specification<Course> spec = Specification.allOf(
                ownedBy(SecurityUtils.keycloakId(caller)),
                hasStatus(status));
        return PageResponse.from(courseRepository.findAll(spec, pageable), courseMapper::toInstructorResponse);
    }

    @Override
    @Transactional
    public CourseDetailResponse create(Jwt caller, CourseCreateRequest request) {
        String title = request.title().trim();
        Course course = Course.builder()
                .instructor(currentUserService.getOrCreate(caller))
                .category(getCategory(request.categoryId()))
                .title(title)
                .slug(SlugUtils.uniqueSlug(baseSlug(title), SLUG_MAX_LENGTH, courseRepository::existsBySlug))
                .description(trimToNull(request.description()))
                .level(request.level())
                .price(request.price())
                .build();
        return toDetail(courseRepository.save(course));
    }

    @Override
    @Transactional
    public CourseDetailResponse update(Long id, CourseUpdateRequest request, Jwt caller) {
        Course course = getOwnedCourse(id, caller);
        String title = request.title().trim();

        // Once a course has been public its slug is in links and bookmarks, so only drafts follow the title
        if (course.getPublishedAt() == null && !course.getTitle().equals(title)) {
            course.setSlug(SlugUtils.uniqueSlug(baseSlug(title), SLUG_MAX_LENGTH,
                    slug -> courseRepository.existsBySlugAndIdNot(slug, id)));
        }
        course.setTitle(title);
        course.setDescription(trimToNull(request.description()));
        course.setLevel(request.level());
        course.setPrice(request.price());
        if (!course.getCategory().getId().equals(request.categoryId())) {
            course.setCategory(getCategory(request.categoryId()));
        }
        return toDetail(courseRepository.saveAndFlush(course));
    }

    @Override
    @Transactional
    public CourseDetailResponse updateThumbnail(Long id, MultipartFile file, Jwt caller) {
        Course course = getOwnedCourse(id, caller);
        String oldUrl = course.getThumbnailUrl();
        String newUrl = fileStorageService.store(file, THUMBNAIL_FOLDER, THUMBNAIL_TYPES, THUMBNAIL_MAX_BYTES);

        course.setThumbnailUrl(newUrl);
        courseRepository.saveAndFlush(course);
        FileTransactionUtils.replaceAfterCompletion(fileStorageService, oldUrl, newUrl);
        return toDetail(course);
    }

    @Override
    @Transactional
    public CourseDetailResponse publish(Long id, Jwt caller) {
        Course course = getOwnedCourse(id, caller);
        if (course.getStatus() == CourseStatus.PUBLISHED) {
            return toDetail(course);
        }

        CourseContentStats stats = courseRepository.findContentStats(id);
        if (stats.getSectionCount() == 0) {
            throw new BadRequestException("Cannot publish: add at least one section");
        }
        if (stats.getLessonCount() == 0) {
            throw new BadRequestException("Cannot publish: add at least one lesson to a section");
        }

        course.publish();
        courseRepository.saveAndFlush(course);
        return courseMapper.toDetailResponse(course, stats);
    }

    @Override
    @Transactional
    public CourseDetailResponse unpublish(Long id, Jwt caller) {
        Course course = getOwnedCourse(id, caller);
        if (course.getStatus() == CourseStatus.DRAFT) {
            return toDetail(course);
        }
        if (enrollmentRepository.existsByCourseIdAndStatus(id, EnrollmentStatus.ACTIVE)) {
            throw new ConflictException("Cannot unpublish: the course has active enrollments");
        }

        course.unpublish();
        return toDetail(courseRepository.saveAndFlush(course));
    }

    @Override
    @Transactional
    public boolean delete(Long id, Jwt caller) {
        Course course = getOwnedCourse(id, caller);

        // Any enrollment (not only ACTIVE) is revenue history and keeps the row; the FK also forbids deleting it
        if (enrollmentRepository.existsByCourseId(id)) {
            if (course.getStatus() != CourseStatus.ARCHIVED) {
                course.archive();
                courseRepository.saveAndFlush(course);
            }
            return true;
        }

        // Collect every stored file first; the rows (sections, lessons, resources) go with the course
        List<String> fileUrls = new ArrayList<>();
        fileUrls.add(course.getThumbnailUrl());
        fileUrls.addAll(lessonRepository.findFileUrlsByCourseId(id));
        fileUrls.addAll(lessonResourceRepository.findFileUrlsByCourseId(id));
        courseRepository.delete(course);
        fileUrls.forEach(url -> FileTransactionUtils.deleteAfterCommit(fileStorageService, url));
        return false;
    }

    private Course getOwnedCourse(Long id, Jwt caller) {
        Course course = courseRepository.findWithInstructorAndCategoryById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", id));
        courseAccessService.requireOwnerOrAdmin(course, caller);
        return course;
    }

    private Category getCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BadRequestException("Category not found: " + categoryId));
    }

    private CourseDetailResponse toDetail(Course course) {
        return courseMapper.toDetailResponse(course, courseRepository.findContentStats(course.getId()));
    }

    private static String baseSlug(String title) {
        String slug = SlugUtils.slugify(title, SLUG_MAX_LENGTH);
        if (slug.isEmpty()) {
            throw new BadRequestException("Title must contain at least one letter or digit");
        }
        return slug;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
