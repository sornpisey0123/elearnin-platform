package com.pisey.elearning.controller;

import com.pisey.elearning.dto.content.LessonCreateRequest;
import com.pisey.elearning.dto.content.LessonOrderRequest;
import com.pisey.elearning.dto.content.LessonResourceResponse;
import com.pisey.elearning.dto.content.LessonResponse;
import com.pisey.elearning.dto.content.LessonUpdateRequest;
import com.pisey.elearning.dto.content.SectionCreateRequest;
import com.pisey.elearning.dto.content.SectionOrderRequest;
import com.pisey.elearning.dto.content.SectionResponse;
import com.pisey.elearning.dto.content.SectionUpdateRequest;
import com.pisey.elearning.service.ContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Course content editing; owner of the course or ADMIN. */
@RestController
@RequestMapping("/api/instructor")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
@Tag(name = "Instructor: content")
public class InstructorContentController {

    private final ContentService contentService;

    // --- sections ---

    @PostMapping("/courses/{courseId}/sections")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a section at the end")
    public SectionResponse createSection(@PathVariable Long courseId,
                                         @Valid @RequestBody SectionCreateRequest request,
                                         @AuthenticationPrincipal Jwt jwt) {
        return contentService.createSection(courseId, request, jwt);
    }

    @PutMapping("/sections/{id}")
    @Operation(summary = "Rename a section")
    public SectionResponse updateSection(@PathVariable Long id,
                                         @Valid @RequestBody SectionUpdateRequest request,
                                         @AuthenticationPrincipal Jwt jwt) {
        return contentService.updateSection(id, request, jwt);
    }

    @DeleteMapping("/sections/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a section with its lessons; later sections move up")
    public void deleteSection(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        contentService.deleteSection(id, jwt);
    }

    @PutMapping("/courses/{courseId}/sections/order")
    @Operation(summary = "Reorder sections (all section ids, each once)")
    public List<SectionResponse> reorderSections(@PathVariable Long courseId,
                                                 @Valid @RequestBody SectionOrderRequest request,
                                                 @AuthenticationPrincipal Jwt jwt) {
        return contentService.reorderSections(courseId, request.sectionIds(), jwt);
    }

    // --- lessons ---

    @PostMapping("/sections/{sectionId}/lessons")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a lesson at the end (VIDEO: videoUrl + durationSeconds; TEXT: body; FILE: upload after)")
    public LessonResponse createLesson(@PathVariable Long sectionId,
                                       @Valid @RequestBody LessonCreateRequest request,
                                       @AuthenticationPrincipal Jwt jwt) {
        return contentService.createLesson(sectionId, request, jwt);
    }

    @PutMapping("/lessons/{id}")
    @Operation(summary = "Update a lesson")
    public LessonResponse updateLesson(@PathVariable Long id,
                                       @Valid @RequestBody LessonUpdateRequest request,
                                       @AuthenticationPrincipal Jwt jwt) {
        return contentService.updateLesson(id, request, jwt);
    }

    @DeleteMapping("/lessons/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a lesson; later lessons move up")
    public void deleteLesson(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        contentService.deleteLesson(id, jwt);
    }

    @PostMapping(path = "/lessons/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload the file of a FILE lesson (pdf, zip, txt, docx, pptx; max 10 MB)")
    public LessonResponse uploadLessonFile(@PathVariable Long id,
                                           @RequestPart("file") MultipartFile file,
                                           @AuthenticationPrincipal Jwt jwt) {
        return contentService.uploadLessonFile(id, file, jwt);
    }

    @PutMapping("/sections/{sectionId}/lessons/order")
    @Operation(summary = "Reorder lessons in a section (all lesson ids, each once)")
    public List<LessonResponse> reorderLessons(@PathVariable Long sectionId,
                                               @Valid @RequestBody LessonOrderRequest request,
                                               @AuthenticationPrincipal Jwt jwt) {
        return contentService.reorderLessons(sectionId, request.lessonIds(), jwt);
    }

    // --- resources ---

    @PostMapping(path = "/lessons/{id}/resources", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Attach a downloadable resource (documents or images; max 10 MB)")
    public LessonResourceResponse addResource(@PathVariable Long id,
                                              @RequestParam @NotBlank @Size(max = 200) String title,
                                              @RequestPart("file") MultipartFile file,
                                              @AuthenticationPrincipal Jwt jwt) {
        return contentService.addResource(id, title, file, jwt);
    }

    @DeleteMapping("/resources/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a resource")
    public void deleteResource(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        contentService.deleteResource(id, jwt);
    }
}
