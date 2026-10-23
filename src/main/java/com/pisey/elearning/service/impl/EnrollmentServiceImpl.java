package com.pisey.elearning.service.impl;

import com.pisey.elearning.dto.common.PageResponse;
import com.pisey.elearning.dto.enrollment.ContinueLearningResponse;
import com.pisey.elearning.dto.enrollment.EnrollmentResponse;
import com.pisey.elearning.dto.enrollment.LessonProgressResponse;
import com.pisey.elearning.dto.enrollment.MyCourseResponse;
import com.pisey.elearning.entity.Course;
import com.pisey.elearning.entity.Enrollment;
import com.pisey.elearning.entity.Lesson;
import com.pisey.elearning.entity.User;
import com.pisey.elearning.entity.enums.CourseStatus;
import com.pisey.elearning.entity.enums.EnrollmentStatus;
import com.pisey.elearning.exception.BadRequestException;
import com.pisey.elearning.exception.ConflictException;
import com.pisey.elearning.exception.ForbiddenException;
import com.pisey.elearning.exception.ResourceNotFoundException;
import com.pisey.elearning.mapper.EnrollmentMapper;
import com.pisey.elearning.repository.CourseRepository;
import com.pisey.elearning.repository.EnrollmentRepository;
import com.pisey.elearning.repository.LessonProgressRepository;
import com.pisey.elearning.repository.LessonRepository;
import com.pisey.elearning.service.CurrentUserService;
import com.pisey.elearning.service.EnrollmentService;
import com.pisey.elearning.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnrollmentServiceImpl implements EnrollmentService {

    private static final List<EnrollmentStatus> LEARNING_STATUSES =
            List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED);
    private static final Pageable FIRST = PageRequest.of(0, 1);

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final CurrentUserService currentUserService;
    private final EnrollmentMapper enrollmentMapper;

    @Override
    @Transactional
    public EnrollmentResponse enroll(Long courseId, Jwt caller) {
        Course course = courseRepository.findWithInstructorAndCategoryById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", courseId));
        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw new BadRequestException("Only published courses can be enrolled in");
        }
        User student = currentUserService.getOrCreate(caller);
        if (course.getInstructor().getId().equals(student.getId())) {
            throw new BadRequestException("You cannot enroll in your own course");
        }
        if (enrollmentRepository.existsByStudentIdAndCourseId(student.getId(), courseId)) {
            throw new ConflictException("Already enrolled in this course");
        }

        Enrollment enrollment;
        try {
            enrollment = enrollmentRepository.saveAndFlush(Enrollment.builder()
                    .student(student)
                    .course(course)
                    .pricePaid(course.getPrice())
                    .build());
        } catch (DataIntegrityViolationException ex) {
            // A concurrent request enrolled first; the unique (student_id, course_id) constraint caught it
            throw new ConflictException("Already enrolled in this course");
        }
        courseRepository.incrementEnrollmentCount(courseId);
        return enrollmentMapper.toResponse(enrollment);
    }

    @Override
    public PageResponse<MyCourseResponse> findMyCourses(Jwt caller, String status, Pageable pageable) {
        return PageResponse.from(
                enrollmentRepository.findByStudentKeycloakIdAndStatusIn(
                        SecurityUtils.keycloakId(caller), parseStatusFilter(status), pageable),
                enrollmentMapper::toMyCourseResponse);
    }

    @Override
    @Transactional
    public LessonProgressResponse completeLesson(Long lessonId, Jwt caller) {
        Lesson lesson = getLesson(lessonId);
        Enrollment enrollment = getEnrollment(lesson.getSection().getCourse().getId(), caller);
        lessonProgressRepository.markCompleted(enrollment.getId(), lessonId);
        return recalculate(enrollment, lessonId, true);
    }

    @Override
    @Transactional
    public LessonProgressResponse uncompleteLesson(Long lessonId, Jwt caller) {
        Lesson lesson = getLesson(lessonId);
        Enrollment enrollment = getEnrollment(lesson.getSection().getCourse().getId(), caller);
        lessonProgressRepository.markIncomplete(enrollment.getId(), lessonId);
        return recalculate(enrollment, lessonId, false);
    }

    @Override
    public ContinueLearningResponse continueLearning(Long courseId, Jwt caller) {
        Enrollment enrollment = getEnrollment(courseId, caller);

        List<Lesson> next = lessonRepository.findIncompleteInOrder(courseId, enrollment.getId(), FIRST);
        if (!next.isEmpty()) {
            return new ContinueLearningResponse(courseId, false, enrollmentMapper.toLessonRef(next.getFirst()));
        }
        // Everything done (or nothing to do): point back at the start of the course
        List<Lesson> first = lessonRepository.findInOrder(courseId, FIRST);
        return first.isEmpty()
                ? new ContinueLearningResponse(courseId, false, null)
                : new ContinueLearningResponse(courseId, true, enrollmentMapper.toLessonRef(first.getFirst()));
    }

    /** progressPct = done / total × 100 (2 dp); 100% completes the enrollment, less reopens a completed one. */
    private LessonProgressResponse recalculate(Enrollment enrollment, Long lessonId, boolean lessonCompleted) {
        Long courseId = enrollment.getCourse().getId();
        long total = lessonRepository.countBySectionCourseId(courseId);
        long done = lessonProgressRepository.countByEnrollmentIdAndCompletedTrue(enrollment.getId());
        BigDecimal pct = total == 0
                ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(done * 100).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);

        if (total > 0 && done >= total) {
            if (enrollment.getStatus() != EnrollmentStatus.COMPLETED) {
                enrollment.markCompleted();
            }
        } else if (enrollment.getStatus() == EnrollmentStatus.COMPLETED) {
            enrollment.reopen(pct);
        } else {
            enrollment.setProgressPct(pct);
        }
        enrollmentRepository.saveAndFlush(enrollment);
        return new LessonProgressResponse(lessonId, lessonCompleted, courseId, enrollment.getStatus(),
                enrollment.getProgressPct(), done, total);
    }

    private Lesson getLesson(Long lessonId) {
        return lessonRepository.findWithCourseById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", lessonId));
    }

    private Enrollment getEnrollment(Long courseId, Jwt caller) {
        return enrollmentRepository.findByStudentKeycloakIdAndCourseIdAndStatusIn(
                        SecurityUtils.keycloakId(caller), courseId, LEARNING_STATUSES)
                .orElseThrow(() -> new ForbiddenException("You are not enrolled in this course"));
    }

    private static List<EnrollmentStatus> parseStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return LEARNING_STATUSES;
        }
        return switch (status.trim().toLowerCase(Locale.ROOT)) {
            case "in-progress" -> List.of(EnrollmentStatus.ACTIVE);
            case "completed" -> List.of(EnrollmentStatus.COMPLETED);
            default -> throw new BadRequestException("Unknown status: " + status + "; use in-progress or completed");
        };
    }
}
