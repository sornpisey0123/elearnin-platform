package com.pisey.elearning.repository;

import com.pisey.elearning.entity.Category;
import com.pisey.elearning.entity.enums.CourseStatus;
import com.pisey.elearning.repository.projection.CategoryCourseCountView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** All categories with their course count for one status, in a single query (categories without courses count 0). */
    @Query("""
            select c.id as id, c.name as name, c.slug as slug, c.description as description,
                   count(co.id) as courseCount
            from Category c
            left join Course co on co.category = c and co.status = :status
            group by c.id, c.name, c.slug, c.description
            order by c.name
            """)
    List<CategoryCourseCountView> findAllWithCourseCount(@Param("status") CourseStatus status);

    Optional<Category> findBySlug(String slug);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);
}
