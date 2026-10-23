package com.pisey.elearning.repository;

import com.pisey.elearning.entity.Course;
import com.pisey.elearning.entity.enums.CourseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, Long> {

    boolean existsByCategoryId(Long categoryId);

    long countByCategoryIdAndStatus(Long categoryId, CourseStatus status);

    /** Fetches instructor and category with the page so summaries don't trigger N+1 queries. */
    @EntityGraph(attributePaths = {"instructor", "category"})
    Page<Course> findByCategoryIdAndStatus(Long categoryId, CourseStatus status, Pageable pageable);
}
