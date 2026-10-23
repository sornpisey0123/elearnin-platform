package com.pisey.elearning.repository;

import com.pisey.elearning.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    /** Ids of lessons in the course that have a quiz, for the outline's {@code hasQuiz} flag. */
    @Query("select distinct q.lesson.id from Quiz q where q.lesson.section.course.id = :courseId")
    List<Long> findLessonIdsWithQuizByCourseId(@Param("courseId") Long courseId);
}
