package com.pisey.elearning.repository;

import com.pisey.elearning.entity.Lesson;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    /** With section, course and instructor, for ownership and access checks. */
    @EntityGraph(attributePaths = {"section", "section.course", "section.course.instructor"})
    Optional<Lesson> findWithCourseById(Long id);

    List<Lesson> findBySectionIdOrderBySortOrder(Long sectionId);

    @Query("select coalesce(max(l.sortOrder), 0) from Lesson l where l.section.id = :sectionId")
    int findMaxSortOrder(@Param("sectionId") Long sectionId);

    long countBySectionCourseId(Long courseId);

    /**
     * Lessons of the course not yet completed in this enrollment, in outline order (section, then lesson).
     * Ask for one row to get "continue learning".
     */
    @Query("""
            select l from Lesson l
            join fetch l.section s
            where s.course.id = :courseId
              and not exists (
                  select 1 from LessonProgress p
                  where p.lesson = l and p.enrollment.id = :enrollmentId and p.completed = true)
            order by s.sortOrder, l.sortOrder
            """)
    List<Lesson> findIncompleteInOrder(@Param("courseId") Long courseId, @Param("enrollmentId") Long enrollmentId,
                                       Pageable pageable);

    /** Lessons of the course in outline order; ask for one row to get the first lesson. */
    @Query("""
            select l from Lesson l
            join fetch l.section s
            where s.course.id = :courseId
            order by s.sortOrder, l.sortOrder
            """)
    List<Lesson> findInOrder(@Param("courseId") Long courseId, Pageable pageable);

    /** Uploaded files of FILE lessons in a course, for cleanup when the course is deleted. */
    @Query("select l.fileUrl from Lesson l where l.section.course.id = :courseId and l.fileUrl is not null")
    List<String> findFileUrlsByCourseId(@Param("courseId") Long courseId);
}
