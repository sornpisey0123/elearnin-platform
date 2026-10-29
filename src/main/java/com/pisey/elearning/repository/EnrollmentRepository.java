package com.pisey.elearning.repository;

import com.pisey.elearning.entity.Enrollment;
import com.pisey.elearning.entity.enums.EnrollmentStatus;
import com.pisey.elearning.repository.projection.EnrollmentTotals;
import com.pisey.elearning.repository.projection.MonthlyEnrollments;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    boolean existsByCourseId(Long courseId);

    @Query("select count(e) as enrollments, coalesce(sum(e.pricePaid), 0) as revenue from Enrollment e")
    EnrollmentTotals findTotals();

    /**
     * Enrollments and revenue per calendar month (UTC) for the last {@code months} months including the current
     * one, oldest first. {@code generate_series} supplies every month, so months without enrollments come back as
     * zeros. Each month is matched as a timestamp range so the {@code (course_id, enrolled_at)} index is used.
     */
    @Query(nativeQuery = true, value = """
            select to_char(m.month, 'YYYY-MM') as month,
                   count(e.id) as enrollments,
                   coalesce(sum(e.price_paid), 0) as revenue
            from generate_series(date_trunc('month', now() at time zone 'UTC') - make_interval(months => :months - 1),
                                 date_trunc('month', now() at time zone 'UTC'),
                                 interval '1 month') as m(month)
            left join enrollments e
                   on e.course_id = :courseId
                  and e.enrolled_at >= m.month at time zone 'UTC'
                  and e.enrolled_at < (m.month + interval '1 month') at time zone 'UTC'
            group by m.month
            order by m.month
            """)
    List<MonthlyEnrollments> findMonthlyTrend(@Param("courseId") Long courseId, @Param("months") int months);

    boolean existsByCourseIdAndStatus(Long courseId, EnrollmentStatus status);

    boolean existsByStudentIdAndCourseId(Long studentId, Long courseId);

    boolean existsByStudentKeycloakIdAndCourseIdAndStatusIn(UUID studentKeycloakId, Long courseId,
                                                           Collection<EnrollmentStatus> statuses);

    Optional<Enrollment> findByStudentKeycloakIdAndCourseIdAndStatusIn(UUID studentKeycloakId, Long courseId,
                                                                      Collection<EnrollmentStatus> statuses);

    /** "My courses": fetches each course with its instructor and category for the summary. */
    @EntityGraph(attributePaths = {"course", "course.instructor", "course.category"})
    Page<Enrollment> findByStudentKeycloakIdAndStatusIn(UUID studentKeycloakId, Collection<EnrollmentStatus> statuses,
                                                        Pageable pageable);
}
