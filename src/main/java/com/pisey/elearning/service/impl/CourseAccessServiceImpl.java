package com.pisey.elearning.service.impl;

import com.pisey.elearning.entity.Course;
import com.pisey.elearning.entity.enums.CourseStatus;
import com.pisey.elearning.entity.enums.EnrollmentStatus;
import com.pisey.elearning.exception.ForbiddenException;
import com.pisey.elearning.repository.EnrollmentRepository;
import com.pisey.elearning.service.CourseAccessService;
import com.pisey.elearning.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseAccessServiceImpl implements CourseAccessService {

    private static final List<EnrollmentStatus> CONTENT_ACCESS_STATUSES =
            List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED);

    private final EnrollmentRepository enrollmentRepository;

    @Override
    public boolean isOwnerOrAdmin(Course course, Jwt caller) {
        if (caller == null) {
            return false;
        }
        return SecurityUtils.isAdmin()
                || course.getInstructor().getKeycloakId().equals(SecurityUtils.keycloakId(caller));
    }

    @Override
    public void requireOwnerOrAdmin(Course course, Jwt caller) {
        if (!isOwnerOrAdmin(course, caller)) {
            throw new ForbiddenException("You can only manage your own courses");
        }
    }

    @Override
    public boolean isEnrolled(Course course, Jwt caller) {
        return caller != null && enrollmentRepository.existsByStudentKeycloakIdAndCourseIdAndStatusIn(
                SecurityUtils.keycloakId(caller), course.getId(), CONTENT_ACCESS_STATUSES);
    }

    @Override
    public boolean isVisible(Course course, Jwt caller) {
        if (course.getStatus() == CourseStatus.PUBLISHED) {
            return true;
        }
        if (isOwnerOrAdmin(course, caller)) {
            return true;
        }
        return course.getStatus() == CourseStatus.ARCHIVED && isEnrolled(course, caller);
    }
}
