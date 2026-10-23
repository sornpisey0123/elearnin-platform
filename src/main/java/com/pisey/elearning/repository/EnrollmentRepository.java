package com.pisey.elearning.repository;

import com.pisey.elearning.entity.Enrollment;
import com.pisey.elearning.entity.enums.EnrollmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    boolean existsByCourseId(Long courseId);

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
