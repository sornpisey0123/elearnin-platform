package com.pisey.elearning.service;

import com.pisey.elearning.dto.content.CourseOutlineResponse;
import com.pisey.elearning.dto.content.LessonCreateRequest;
import com.pisey.elearning.dto.content.LessonResourceResponse;
import com.pisey.elearning.dto.content.LessonResponse;
import com.pisey.elearning.dto.content.LessonUpdateRequest;
import com.pisey.elearning.dto.content.SectionCreateRequest;
import com.pisey.elearning.dto.content.SectionResponse;
import com.pisey.elearning.dto.content.SectionUpdateRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Sections, lessons and lesson resources. Write methods require the course owner or an ADMIN.
 */
public interface ContentService {

    /** Public table of contents, visible like the course itself; {@code caller} may be null. */
    CourseOutlineResponse getOutline(Long courseId, Jwt caller);

    /**
     * Full lesson content. Allowed for the owner/admin, enrolled students (ACTIVE or COMPLETED), and anyone
     * signed in for a preview lesson of a published course; otherwise 403 "Enroll to access this lesson".
     */
    LessonResponse getLesson(Long lessonId, Jwt caller);

    // --- sections ---

    SectionResponse createSection(Long courseId, SectionCreateRequest request, Jwt caller);

    SectionResponse updateSection(Long sectionId, SectionUpdateRequest request, Jwt caller);

    /** Deletes the section with its lessons and files, then closes the gap in the order. */
    void deleteSection(Long sectionId, Jwt caller);

    /** {@code sectionIds} must be exactly the course's sections, each once; otherwise 400. */
    List<SectionResponse> reorderSections(Long courseId, List<Long> sectionIds, Jwt caller);

    // --- lessons ---

    LessonResponse createLesson(Long sectionId, LessonCreateRequest request, Jwt caller);

    LessonResponse updateLesson(Long lessonId, LessonUpdateRequest request, Jwt caller);

    void deleteLesson(Long lessonId, Jwt caller);

    /** FILE lessons only. */
    LessonResponse uploadLessonFile(Long lessonId, MultipartFile file, Jwt caller);

    /** {@code lessonIds} must be exactly the section's lessons, each once; otherwise 400. */
    List<LessonResponse> reorderLessons(Long sectionId, List<Long> lessonIds, Jwt caller);

    // --- resources ---

    LessonResourceResponse addResource(Long lessonId, String title, MultipartFile file, Jwt caller);

    void deleteResource(Long resourceId, Jwt caller);
}
