package com.pisey.elearning.service;

import com.pisey.elearning.dto.quiz.AttemptResultResponse;
import com.pisey.elearning.dto.quiz.AttemptStartResponse;
import com.pisey.elearning.dto.quiz.AttemptSubmitRequest;
import com.pisey.elearning.dto.quiz.AttemptSummaryResponse;
import com.pisey.elearning.dto.quiz.QuizCreateRequest;
import com.pisey.elearning.dto.quiz.QuizResponse;
import com.pisey.elearning.dto.quiz.QuizSummaryResponse;
import com.pisey.elearning.dto.quiz.QuizUpdateRequest;
import com.pisey.elearning.dto.quiz.StudentQuizResponse;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * Quizzes attached to a lesson or a section, and students' attempts at them. Instructor methods require the course
 * owner or an ADMIN; taking a quiz requires an enrollment (ACTIVE or COMPLETED).
 */
public interface QuizService {

    // --- instructor ---

    QuizResponse createForLesson(Long lessonId, QuizCreateRequest request, Jwt caller);

    QuizResponse createForSection(Long sectionId, QuizCreateRequest request, Jwt caller);

    /** With the correct answers. */
    QuizResponse getForInstructor(Long quizId, Jwt caller);

    /** Replaces the settings and every question; 409 once anyone has attempted the quiz. */
    QuizResponse update(Long quizId, QuizUpdateRequest request, Jwt caller);

    /** Deletes the quiz with its attempts. */
    void delete(Long quizId, Jwt caller);

    // --- students ---

    /** The course's quizzes in outline order, visible like the course itself. */
    List<QuizSummaryResponse> findByCourse(Long courseId, Jwt caller);

    /** Without the correct answers. Enrolled students, plus the owner and admins for previewing. */
    StudentQuizResponse getForStudent(Long quizId, Jwt caller);

    /** Returns the caller's open, unexpired attempt if there is one ({@code resumed}); otherwise starts a new one. */
    AttemptStartResponse startAttempt(Long quizId, Jwt caller);

    /**
     * Grades the attempt. Only its owner, only once (409 after). Submitting later than the time limit plus a
     * 30-second grace period scores 0 and fails.
     */
    AttemptResultResponse submit(Long attemptId, AttemptSubmitRequest request, Jwt caller);

    /** The caller's attempts at the quiz, newest first. */
    List<AttemptSummaryResponse> findMyAttempts(Long quizId, Jwt caller);
}
