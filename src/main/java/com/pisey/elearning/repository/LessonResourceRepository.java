package com.pisey.elearning.repository;

import com.pisey.elearning.entity.LessonResource;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LessonResourceRepository extends JpaRepository<LessonResource, Long> {

    @EntityGraph(attributePaths = {"lesson", "lesson.section", "lesson.section.course", "lesson.section.course.instructor"})
    Optional<LessonResource> findWithCourseById(Long id);

    /** Resource files in a course, for cleanup when the course is deleted. */
    @Query("select r.fileUrl from LessonResource r where r.lesson.section.course.id = :courseId")
    List<String> findFileUrlsByCourseId(@Param("courseId") Long courseId);
}
