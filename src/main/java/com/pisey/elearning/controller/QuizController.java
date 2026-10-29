package com.pisey.elearning.controller;

import com.pisey.elearning.dto.quiz.AttemptResultResponse;
import com.pisey.elearning.dto.quiz.AttemptStartResponse;
import com.pisey.elearning.dto.quiz.AttemptSubmitRequest;
import com.pisey.elearning.dto.quiz.AttemptSummaryResponse;
import com.pisey.elearning.dto.quiz.QuizSummaryResponse;
import com.pisey.elearning.dto.quiz.StudentQuizResponse;
import com.pisey.elearning.service.QuizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Taking quizzes; requires a token (see SecurityConfig) and, to answer, an enrollment. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Quizzes")
public class QuizController {

    private final QuizService quizService;

    @GetMapping("/api/courses/{courseId}/quizzes")
    @Operation(summary = "The course's quizzes in outline order")
    public List<QuizSummaryResponse> findByCourse(@PathVariable Long courseId, @AuthenticationPrincipal Jwt jwt) {
        return quizService.findByCourse(courseId, jwt);
    }

    @GetMapping("/api/quizzes/{id}")
    @Operation(summary = "Quiz questions and options, without the correct answers")
    public StudentQuizResponse get(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return quizService.getForStudent(id, jwt);
    }

    @PostMapping("/api/quizzes/{id}/attempts")
    @Operation(summary = "Start an attempt (201), or resume the open unexpired one (200)")
    public ResponseEntity<AttemptStartResponse> start(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        AttemptStartResponse response = quizService.startAttempt(id, jwt);
        return ResponseEntity.status(response.resumed() ? HttpStatus.OK : HttpStatus.CREATED).body(response);
    }

    @PostMapping("/api/attempts/{attemptId}/submit")
    @Operation(summary = "Submit answers once and get the graded result")
    public AttemptResultResponse submit(@PathVariable Long attemptId,
                                        @Valid @RequestBody AttemptSubmitRequest request,
                                        @AuthenticationPrincipal Jwt jwt) {
        return quizService.submit(attemptId, request, jwt);
    }

    @GetMapping("/api/quizzes/{id}/attempts/me")
    @Operation(summary = "My attempts at this quiz, newest first")
    public List<AttemptSummaryResponse> myAttempts(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return quizService.findMyAttempts(id, jwt);
    }
}
