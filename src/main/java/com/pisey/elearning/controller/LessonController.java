package com.pisey.elearning.controller;

import com.pisey.elearning.dto.content.LessonResponse;
import com.pisey.elearning.service.ContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lesson content; requires a token (see SecurityConfig). */
@RestController
@RequestMapping("/api/lessons")
@RequiredArgsConstructor
@Tag(name = "Lessons")
public class LessonController {

    private final ContentService contentService;

    @GetMapping("/{id}")
    @Operation(summary = "Lesson content (preview, enrolled, owner or admin)")
    public LessonResponse getLesson(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return contentService.getLesson(id, jwt);
    }
}
