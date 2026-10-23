package com.pisey.elearning.service.impl;

import com.pisey.elearning.dto.content.CourseOutlineResponse;
import com.pisey.elearning.dto.content.CourseOutlineResponse.SectionOutline;
import com.pisey.elearning.dto.content.LessonCreateRequest;
import com.pisey.elearning.dto.content.LessonResourceResponse;
import com.pisey.elearning.dto.content.LessonResponse;
import com.pisey.elearning.dto.content.LessonUpdateRequest;
import com.pisey.elearning.dto.content.SectionCreateRequest;
import com.pisey.elearning.dto.content.SectionResponse;
import com.pisey.elearning.dto.content.SectionUpdateRequest;
import com.pisey.elearning.entity.BaseEntity;
import com.pisey.elearning.entity.Course;
import com.pisey.elearning.entity.Lesson;
import com.pisey.elearning.entity.LessonResource;
import com.pisey.elearning.entity.Ordered;
import com.pisey.elearning.entity.Section;
import com.pisey.elearning.entity.enums.CourseStatus;
import com.pisey.elearning.entity.enums.LessonType;
import com.pisey.elearning.exception.BadRequestException;
import com.pisey.elearning.exception.ForbiddenException;
import com.pisey.elearning.exception.ResourceNotFoundException;
import com.pisey.elearning.mapper.ContentMapper;
import com.pisey.elearning.repository.CourseRepository;
import com.pisey.elearning.repository.LessonRepository;
import com.pisey.elearning.repository.LessonResourceRepository;
import com.pisey.elearning.repository.QuizRepository;
import com.pisey.elearning.repository.SectionRepository;
import com.pisey.elearning.service.ContentService;
import com.pisey.elearning.service.CourseAccessService;
import com.pisey.elearning.service.FileStorageService;
import com.pisey.elearning.utils.FileTransactionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentServiceImpl implements ContentService {

    static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    static final Set<String> LESSON_FILE_TYPES = Set.of(
            "application/pdf",
            "application/zip",
            "text/plain",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation");
    static final Set<String> RESOURCE_TYPES = Stream.concat(
                    LESSON_FILE_TYPES.stream(),
                    Stream.of("image/png", "image/jpeg", "image/webp"))
            .collect(Collectors.toUnmodifiableSet());

    private final CourseRepository courseRepository;
    private final SectionRepository sectionRepository;
    private final LessonRepository lessonRepository;
    private final LessonResourceRepository lessonResourceRepository;
    private final QuizRepository quizRepository;
    private final CourseAccessService courseAccessService;
    private final FileStorageService fileStorageService;
    private final ContentMapper contentMapper;

    @Override
    public CourseOutlineResponse getOutline(Long courseId, Jwt caller) {
        Course course = courseRepository.findWithInstructorAndCategoryById(courseId)
                .filter(found -> courseAccessService.isVisible(found, caller))
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", courseId));

        Set<Long> lessonsWithQuiz = new HashSet<>(quizRepository.findLessonIdsWithQuizByCourseId(courseId));
        List<SectionOutline> sections = sectionRepository.findOutline(courseId).stream()
                .map(section -> new SectionOutline(section.getId(), section.getTitle(), section.getSortOrder(),
                        section.getLessons().stream()
                                .map(lesson -> contentMapper.toLessonOutline(lesson, lessonsWithQuiz.contains(lesson.getId())))
                                .toList()))
                .toList();
        return new CourseOutlineResponse(course.getId(), course.getTitle(), sections);
    }

    @Override
    public LessonResponse getLesson(Long lessonId, Jwt caller) {
        Lesson lesson = lessonRepository.findWithCourseById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", lessonId));
        Course course = lesson.getSection().getCourse();

        if (courseAccessService.isOwnerOrAdmin(course, caller) || courseAccessService.isEnrolled(course, caller)) {
            return contentMapper.toLessonResponse(lesson);
        }
        // Don't reveal lessons of courses the caller can't even see
        if (!courseAccessService.isVisible(course, caller)) {
            throw new ResourceNotFoundException("Lesson", "id", lessonId);
        }
        if (lesson.isPreview() && course.getStatus() == CourseStatus.PUBLISHED) {
            return contentMapper.toLessonResponse(lesson);
        }
        throw new ForbiddenException("Enroll to access this lesson");
    }

    // --- sections ---

    @Override
    @Transactional
    public SectionResponse createSection(Long courseId, SectionCreateRequest request, Jwt caller) {
        Course course = courseRepository.findWithInstructorAndCategoryById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", courseId));
        courseAccessService.requireOwnerOrAdmin(course, caller);

        Section section = Section.builder()
                .course(course)
                .title(request.title().trim())
                .sortOrder(sectionRepository.findMaxSortOrder(courseId) + 1)
                .build();
        return contentMapper.toSectionResponse(sectionRepository.save(section));
    }

    @Override
    @Transactional
    public SectionResponse updateSection(Long sectionId, SectionUpdateRequest request, Jwt caller) {
        Section section = getOwnedSection(sectionId, caller);
        section.setTitle(request.title().trim());
        return contentMapper.toSectionResponse(sectionRepository.saveAndFlush(section));
    }

    @Override
    @Transactional
    public void deleteSection(Long sectionId, Jwt caller) {
        Section section = getOwnedSection(sectionId, caller);
        Course course = section.getCourse();
        List<String> fileUrls = section.getLessons().stream()
                .flatMap(ContentServiceImpl::storedFiles)
                .toList();

        // Orphan removal deletes the section, its lessons and resources (quizzes and progress cascade in the DB)
        course.removeSection(section);
        // Delete before renumbering so the removed row's position can't collide with the temporary ones
        sectionRepository.flush();
        renumber(course.getSections(), sectionRepository::flush);
        fileUrls.forEach(url -> FileTransactionUtils.deleteAfterCommit(fileStorageService, url));
    }

    @Override
    @Transactional
    public List<SectionResponse> reorderSections(Long courseId, List<Long> sectionIds, Jwt caller) {
        Course course = courseRepository.findWithInstructorAndCategoryById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", courseId));
        courseAccessService.requireOwnerOrAdmin(course, caller);

        List<Section> ordered = inRequestedOrder(
                sectionRepository.findByCourseIdOrderBySortOrder(courseId), sectionIds, "sectionIds", "section");
        renumber(ordered, sectionRepository::flush);
        return ordered.stream().map(contentMapper::toSectionResponse).toList();
    }

    // --- lessons ---

    @Override
    @Transactional
    public LessonResponse createLesson(Long sectionId, LessonCreateRequest request, Jwt caller) {
        Section section = getOwnedSection(sectionId, caller);
        Lesson lesson = Lesson.builder()
                .section(section)
                .title(request.title().trim())
                .preview(Boolean.TRUE.equals(request.preview()))
                .sortOrder(lessonRepository.findMaxSortOrder(sectionId) + 1)
                .build();
        applyContent(lesson, request.type(), request.videoUrl(), request.durationSeconds(), request.body());
        return contentMapper.toLessonResponse(lessonRepository.save(lesson));
    }

    @Override
    @Transactional
    public LessonResponse updateLesson(Long lessonId, LessonUpdateRequest request, Jwt caller) {
        Lesson lesson = getOwnedLesson(lessonId, caller);
        lesson.setTitle(request.title().trim());
        lesson.setPreview(Boolean.TRUE.equals(request.preview()));
        applyContent(lesson, request.type(), request.videoUrl(), request.durationSeconds(), request.body());
        return contentMapper.toLessonResponse(lessonRepository.saveAndFlush(lesson));
    }

    @Override
    @Transactional
    public void deleteLesson(Long lessonId, Jwt caller) {
        Lesson lesson = getOwnedLesson(lessonId, caller);
        Section section = lesson.getSection();
        List<String> fileUrls = storedFiles(lesson).toList();

        section.removeLesson(lesson);
        lessonRepository.flush();
        renumber(section.getLessons(), lessonRepository::flush);
        fileUrls.forEach(url -> FileTransactionUtils.deleteAfterCommit(fileStorageService, url));
    }

    @Override
    @Transactional
    public LessonResponse uploadLessonFile(Long lessonId, MultipartFile file, Jwt caller) {
        Lesson lesson = getOwnedLesson(lessonId, caller);
        if (lesson.getType() != LessonType.FILE) {
            throw new BadRequestException("Only FILE lessons take an uploaded file; this lesson is " + lesson.getType());
        }
        String oldUrl = lesson.getFileUrl();
        String newUrl = fileStorageService.store(file, "lessons", LESSON_FILE_TYPES, MAX_FILE_BYTES);

        lesson.setFileUrl(newUrl);
        lessonRepository.saveAndFlush(lesson);
        FileTransactionUtils.replaceAfterCompletion(fileStorageService, oldUrl, newUrl);
        return contentMapper.toLessonResponse(lesson);
    }

    @Override
    @Transactional
    public List<LessonResponse> reorderLessons(Long sectionId, List<Long> lessonIds, Jwt caller) {
        getOwnedSection(sectionId, caller);

        List<Lesson> ordered = inRequestedOrder(
                lessonRepository.findBySectionIdOrderBySortOrder(sectionId), lessonIds, "lessonIds", "lesson");
        renumber(ordered, lessonRepository::flush);
        return ordered.stream().map(contentMapper::toLessonResponse).toList();
    }

    // --- resources ---

    @Override
    @Transactional
    public LessonResourceResponse addResource(Long lessonId, String title, MultipartFile file, Jwt caller) {
        Lesson lesson = getOwnedLesson(lessonId, caller);
        String url = fileStorageService.store(file, "resources", RESOURCE_TYPES, MAX_FILE_BYTES);

        LessonResource resource = LessonResource.builder()
                .title(title.trim())
                .fileUrl(url)
                .mimeType(file.getContentType())
                .sizeBytes(file.getSize())
                .build();
        lesson.addResource(resource);
        lessonResourceRepository.save(resource);
        // Nothing to delete on commit; on rollback remove the orphaned upload
        FileTransactionUtils.replaceAfterCompletion(fileStorageService, null, url);
        return contentMapper.toResourceResponse(resource);
    }

    @Override
    @Transactional
    public void deleteResource(Long resourceId, Jwt caller) {
        LessonResource resource = lessonResourceRepository.findWithCourseById(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource", "id", resourceId));
        courseAccessService.requireOwnerOrAdmin(resource.getLesson().getSection().getCourse(), caller);

        resource.getLesson().removeResource(resource);
        FileTransactionUtils.deleteAfterCommit(fileStorageService, resource.getFileUrl());
    }

    // --- helpers ---

    private Section getOwnedSection(Long sectionId, Jwt caller) {
        Section section = sectionRepository.findWithCourseById(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Section", "id", sectionId));
        courseAccessService.requireOwnerOrAdmin(section.getCourse(), caller);
        return section;
    }

    private Lesson getOwnedLesson(Long lessonId, Jwt caller) {
        Lesson lesson = lessonRepository.findWithCourseById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", lessonId));
        courseAccessService.requireOwnerOrAdmin(lesson.getSection().getCourse(), caller);
        return lesson;
    }

    /** Sets the content fields for the type, clearing the ones that belong to other types. */
    private void applyContent(Lesson lesson, LessonType type, String videoUrl, Integer durationSeconds, String body) {
        switch (type) {
            case VIDEO -> {
                if (!StringUtils.hasText(videoUrl) || durationSeconds == null) {
                    throw new BadRequestException("VIDEO lessons need videoUrl and durationSeconds");
                }
                lesson.setVideoUrl(videoUrl.trim());
                lesson.setDurationSeconds(durationSeconds);
                lesson.setBody(null);
            }
            case TEXT -> {
                if (!StringUtils.hasText(body)) {
                    throw new BadRequestException("TEXT lessons need a body");
                }
                lesson.setBody(body);
                lesson.setVideoUrl(null);
                lesson.setDurationSeconds(null);
            }
            case FILE -> {
                lesson.setVideoUrl(null);
                lesson.setDurationSeconds(null);
                lesson.setBody(null);
            }
        }
        if (type != LessonType.FILE && lesson.getFileUrl() != null) {
            FileTransactionUtils.deleteAfterCommit(fileStorageService, lesson.getFileUrl());
            lesson.setFileUrl(null);
        }
        lesson.setType(type);
    }

    /**
     * Returns {@code current} in the order of {@code requestedIds}, which must contain every current id exactly
     * once (no extras, none missing, no duplicates).
     */
    private static <T extends BaseEntity> List<T> inRequestedOrder(List<T> current, List<Long> requestedIds,
                                                                  String field, String noun) {
        Map<Long, T> byId = current.stream().collect(Collectors.toMap(BaseEntity::getId, Function.identity()));
        Set<Long> requested = new HashSet<>(requestedIds);
        if (requested.size() != requestedIds.size() || !requested.equals(byId.keySet())) {
            throw new BadRequestException("%s must list every %s id exactly once; expected %s"
                    .formatted(field, noun, current.stream().map(BaseEntity::getId).toList()));
        }
        return requestedIds.stream().map(byId::get).toList();
    }

    /**
     * Assigns 1..n in list order. Moving every item above the current maximum first keeps each intermediate
     * state free of {@code (parent, sort_order)} duplicates even if the constraint were not deferred. (The
     * skill's "shift negative" variant is not used because the entities validate {@code sortOrder >= 0}.)
     */
    private static void renumber(List<? extends Ordered> items, Runnable flush) {
        int base = Math.max(items.size(), items.stream().mapToInt(Ordered::getSortOrder).max().orElse(0));
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setSortOrder(base + i + 1);
        }
        flush.run();
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setSortOrder(i + 1);
        }
        flush.run();
    }

    private static Stream<String> storedFiles(Lesson lesson) {
        return Stream.concat(
                Stream.ofNullable(lesson.getFileUrl()),
                lesson.getResources().stream().map(LessonResource::getFileUrl));
    }
}
