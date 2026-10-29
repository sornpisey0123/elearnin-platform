package com.pisey.elearning.repository;

import com.pisey.elearning.entity.QuizAttempt;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    boolean existsByQuizId(Long quizId);

    /** The student's newest unsubmitted attempt, to resume instead of starting another. */
    Optional<QuizAttempt> findFirstByQuizIdAndStudentKeycloakIdAndSubmittedAtIsNullOrderByStartedAtDesc(
            Long quizId, UUID studentKeycloakId);

    List<QuizAttempt> findByQuizIdAndStudentKeycloakIdOrderByStartedAtDescIdDesc(Long quizId, UUID studentKeycloakId);

    /** With the quiz and the student, for the owner check and grading. */
    @EntityGraph(attributePaths = {"quiz", "student"})
    Optional<QuizAttempt> findWithQuizAndStudentById(Long id);
}
