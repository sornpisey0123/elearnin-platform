package com.pisey.elearning.repository;

import com.pisey.elearning.entity.Section;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SectionRepository extends JpaRepository<Section, Long> {

    /** With the course and its instructor, for ownership checks. */
    @EntityGraph(attributePaths = {"course", "course.instructor"})
    Optional<Section> findWithCourseById(Long id);

    /** Sections in order with their lessons (ordered by {@code @OrderBy}) in one query. */
    @Query("""
            select distinct s from Section s
            left join fetch s.lessons
            where s.course.id = :courseId
            order by s.sortOrder
            """)
    List<Section> findOutline(@Param("courseId") Long courseId);

    List<Section> findByCourseIdOrderBySortOrder(Long courseId);

    @Query("select coalesce(max(s.sortOrder), 0) from Section s where s.course.id = :courseId")
    int findMaxSortOrder(@Param("courseId") Long courseId);
}
