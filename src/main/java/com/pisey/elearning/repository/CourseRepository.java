package com.pisey.elearning.repository;

import com.pisey.elearning.entity.Course;
import com.pisey.elearning.entity.enums.CourseStatus;
import com.pisey.elearning.repository.projection.CourseContentStats;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long>, JpaSpecificationExecutor<Course> {

    boolean existsByCategoryId(Long categoryId);

    long countByCategoryIdAndStatus(Long categoryId, CourseStatus status);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    /** Fetches instructor and category with the page so summaries don't trigger N+1 queries. */
    @EntityGraph(attributePaths = {"instructor", "category"})
    Page<Course> findByCategoryIdAndStatus(Long categoryId, CourseStatus status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"instructor", "category"})
    Page<Course> findAll(Specification<Course> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"instructor", "category"})
    Optional<Course> findBySlug(String slug);

    @EntityGraph(attributePaths = {"instructor", "category"})
    Optional<Course> findWithInstructorAndCategoryById(Long id);

    /** Always one row; zeros for a course without sections. */
    @Query("""
            select count(distinct s.id) as sectionCount,
                   count(l.id) as lessonCount,
                   coalesce(sum(l.durationSeconds), 0) as totalDurationSeconds
            from Section s
            left join s.lessons l
            where s.course.id = :courseId
            """)
    CourseContentStats findContentStats(@Param("courseId") Long courseId);
}
