package com.pisey.elearning.repository;

import com.pisey.elearning.entity.Enrollment;
import com.pisey.elearning.entity.enums.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    boolean existsByCourseId(Long courseId);

    boolean existsByCourseIdAndStatus(Long courseId, EnrollmentStatus status);

    boolean existsByStudentKeycloakIdAndCourseIdAndStatusIn(UUID studentKeycloakId, Long courseId,
                                                           Collection<EnrollmentStatus> statuses);
}
