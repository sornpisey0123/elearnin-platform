package com.pisey.elearning.repository;

import com.pisey.elearning.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByCourseIdAndStudentId(Long courseId, Long studentId);

    /** Average of every review on the instructor's courses; null without reviews. */
    @Query("select avg(r.rating) from Review r where r.course.instructor.keycloakId = :instructorKeycloakId")
    Double findAverageRatingByInstructor(@Param("instructorKeycloakId") UUID instructorKeycloakId);

    /** Fetches each author with the page so the list doesn't query per review. */
    @EntityGraph(attributePaths = "student")
    Page<Review> findByCourseId(Long courseId, Pageable pageable);

    @EntityGraph(attributePaths = "student")
    Optional<Review> findByCourseIdAndStudentKeycloakId(Long courseId, UUID studentKeycloakId);

    /** With the author and the course, for the author check and the rating recalculation. */
    @EntityGraph(attributePaths = {"student", "course"})
    Optional<Review> findWithStudentAndCourseById(Long id);

    /**
     * Recomputes the course's cached {@code avg_rating} (2 dp, 0 without reviews) and {@code review_count} from the
     * reviews table in one statement. Pending review changes are flushed first so they are counted.
     */
    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            update courses c
            set avg_rating = coalesce(s.avg_rating, 0), review_count = s.review_count
            from (select round(avg(rating), 2) as avg_rating, count(*) as review_count
                  from reviews where course_id = :courseId) s
            where c.id = :courseId
            """)
    void recalculateCourseRating(@Param("courseId") Long courseId);
}
