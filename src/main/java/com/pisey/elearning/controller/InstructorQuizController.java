package com.pisey.elearning.controller;

import com.pisey.elearning.dto.quiz.QuizCreateRequest;
import com.pisey.elearning.dto.quiz.QuizResponse;
import com.pisey.elearning.dto.quiz.QuizUpdateRequest;
import com.pisey.elearning.service.QuizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Quiz authoring; owner of the course or ADMIN. */
@RestController
@RequestMapping("/api/instructor")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
@Tag(name = "Instructor: quizzes")
public class InstructorQuizController {

    private final QuizService quizService;

    @PostMapping("/lessons/{lessonId}/quizzes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a quiz to a lesson")
    public QuizResponse createForLesson(@PathVariable Long lessonId,
                                        @Valid @RequestBody QuizCreateRequest request,
                                        @AuthenticationPrincipal Jwt jwt) {
        return quizService.createForLesson(lessonId, request, jwt);
    }

    @PostMapping("/sections/{sectionId}/quizzes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a quiz to a section")
    public QuizResponse createForSection(@PathVariable Long sectionId,
                                         @Valid @RequestBody QuizCreateRequest request,
                                         @AuthenticationPrincipal Jwt jwt) {
        return quizService.createForSection(sectionId, request, jwt);
    }

    @GetMapping("/quizzes/{id}")
    @Operation(summary = "Quiz with the correct answers")
    public QuizResponse get(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return quizService.getForInstructor(id, jwt);
    }

    @PutMapping("/quizzes/{id}")
    @Operation(summary = "Replace settings and questions (409 once the quiz has attempts)")
    public QuizResponse update(@PathVariable Long id,
                               @Valid @RequestBody QuizUpdateRequest request,
                               @AuthenticationPrincipal Jwt jwt) {
        return quizService.update(id, request, jwt);
    }

    @DeleteMapping("/quizzes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a quiz with its attempts")
    public void delete(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        quizService.delete(id, jwt);
    }
}
