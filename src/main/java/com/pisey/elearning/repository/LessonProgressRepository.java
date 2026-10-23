package com.pisey.elearning.repository;

import com.pisey.elearning.entity.LessonProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, Long> {

    /**
     * Idempotent "mark done": one atomic statement, so concurrent requests can neither duplicate the row nor
     * fail on {@code uk_lesson_progress_enrollment_id_lesson_id}. Keeps the first completion time.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into lesson_progress (enrollment_id, lesson_id, completed, completed_at, created_at, updated_at)
            values (:enrollmentId, :lessonId, true, now(), now(), now())
            on conflict (enrollment_id, lesson_id) do update
            set completed = true,
                completed_at = coalesce(lesson_progress.completed_at, now()),
                updated_at = now()
            """)
    void markCompleted(@Param("enrollmentId") Long enrollmentId, @Param("lessonId") Long lessonId);

    @Modifying
    @Query(nativeQuery = true, value = """
            update lesson_progress
            set completed = false, completed_at = null, updated_at = now()
            where enrollment_id = :enrollmentId and lesson_id = :lessonId
            """)
    void markIncomplete(@Param("enrollmentId") Long enrollmentId, @Param("lessonId") Long lessonId);

    long countByEnrollmentIdAndCompletedTrue(Long enrollmentId);

    boolean existsByEnrollmentIdAndLessonIdAndCompletedTrue(Long enrollmentId, Long lessonId);
}
