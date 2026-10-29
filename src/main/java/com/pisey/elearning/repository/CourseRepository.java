package com.pisey.elearning.repository;

import com.pisey.elearning.entity.Course;
import com.pisey.elearning.entity.enums.CourseStatus;
import com.pisey.elearning.entity.enums.EnrollmentStatus;
import com.pisey.elearning.repository.projection.CourseContentStats;
import com.pisey.elearning.repository.projection.InstructorCourseStats;
import com.pisey.elearning.repository.projection.StatusCount;
import com.pisey.elearning.repository.projection.TopCourseView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, Long>, JpaSpecificationExecutor<Course> {

    boolean existsByCategoryId(Long categoryId);

    /** Only statuses that have courses; callers zero-fill the rest. */
    @Query("select c.status as status, count(c) as count from Course c group by c.status")
    List<StatusCount> countGroupedByStatus();

    /** Courses in {@code statuses} by cached enrollment count, most first; the page size is the limit. */
    @Query("""
            select c.id as id, c.title as title, c.slug as slug, c.status as status, i.fullName as instructorName,
                   c.enrollmentCount as enrollmentCount, c.avgRating as avgRating, c.reviewCount as reviewCount
            from Course c join c.instructor i
            where c.status in :statuses
            order by c.enrollmentCount desc, c.id
            """)
    List<TopCourseView> findTopByEnrollments(@Param("statuses") Collection<CourseStatus> statuses, Pageable pageable);

    /** Courses in {@code statuses} with at least {@code minReviews} reviews, by cached rating, best first. */
    @Query("""
            select c.id as id, c.title as title, c.slug as slug, c.status as status, i.fullName as instructorName,
                   c.enrollmentCount as enrollmentCount, c.avgRating as avgRating, c.reviewCount as reviewCount
            from Course c join c.instructor i
            where c.status in :statuses and c.reviewCount >= :minReviews
            order by c.avgRating desc, c.reviewCount desc, c.id
            """)
    List<TopCourseView> findTopByRating(@Param("statuses") Collection<CourseStatus> statuses,
                                        @Param("minReviews") int minReviews, Pageable pageable);

    /** Every course of the instructor (any status) with enrollment counts and revenue, newest course first. */
    @Query("""
            select c.id as courseId, c.title as title, c.status as status,
                   count(e.id) as enrollments,
                   coalesce(sum(case when e.status = :completed then 1 else 0 end), 0) as completedEnrollments,
                   coalesce(sum(e.pricePaid), 0) as revenue,
                   c.avgRating as avgRating, c.reviewCount as reviewCount
            from Course c
            left join Enrollment e on e.course = c
            where c.instructor.keycloakId = :instructorKeycloakId
            group by c.id, c.title, c.status, c.avgRating, c.reviewCount, c.createdAt
            order by c.createdAt desc, c.id desc
            """)
    List<InstructorCourseStats> findInstructorCourseStats(@Param("instructorKeycloakId") UUID instructorKeycloakId,
                                                          @Param("completed") EnrollmentStatus completed);

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

    /** Atomic counter bump in the enrolling transaction (no lost updates under concurrent enrollments). */
    @Modifying
    @Query("update Course c set c.enrollmentCount = c.enrollmentCount + 1 where c.id = :courseId")
    void incrementEnrollmentCount(@Param("courseId") Long courseId);

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
