package com.pisey.elearning.repository;

import com.pisey.elearning.entity.Quiz;
import com.pisey.elearning.entity.QuizQuestion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    /** Ids of lessons in the course that have a quiz, for the outline's {@code hasQuiz} flag. */
    @Query("select distinct q.lesson.id from Quiz q where q.lesson.section.course.id = :courseId")
    List<Long> findLessonIdsWithQuizByCourseId(@Param("courseId") Long courseId);

    /** With the lesson or section, the course and its instructor, for ownership and access checks. */
    @EntityGraph(attributePaths = {
            "lesson", "lesson.section", "lesson.section.course", "lesson.section.course.instructor",
            "section", "section.course", "section.course.instructor"})
    Optional<Quiz> findWithCourseById(Long id);

    /**
     * The quiz's questions in order, each with its options, in one query. Call it before mapping or grading a quiz
     * so that walking {@code quiz.getQuestions()} doesn't load options question by question.
     */
    @Query("""
            select qq from QuizQuestion qq
            left join fetch qq.options
            where qq.quiz.id = :quizId
            order by qq.sortOrder
            """)
    List<QuizQuestion> findQuestionsWithOptions(@Param("quizId") Long quizId);

    /** Lesson and section quizzes of the course, in outline order (a section's own quizzes after its lessons'). */
    @Query("""
            select q from Quiz q
            left join q.lesson l
            left join l.section ls
            left join q.section s
            where ls.course.id = :courseId or s.course.id = :courseId
            order by coalesce(ls.sortOrder, s.sortOrder), coalesce(l.sortOrder, 2147483647), q.id
            """)
    List<Quiz> findByCourseId(@Param("courseId") Long courseId);
}
