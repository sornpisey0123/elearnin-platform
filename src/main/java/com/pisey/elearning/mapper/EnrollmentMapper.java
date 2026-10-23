package com.pisey.elearning.mapper;

import com.pisey.elearning.dto.enrollment.ContinueLearningResponse.LessonRef;
import com.pisey.elearning.dto.enrollment.EnrollmentResponse;
import com.pisey.elearning.dto.enrollment.MyCourseResponse;
import com.pisey.elearning.entity.Enrollment;
import com.pisey.elearning.entity.Lesson;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = CourseMapper.class, injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface EnrollmentMapper {

    @Mapping(target = "courseId", source = "course.id")
    EnrollmentResponse toResponse(Enrollment enrollment);

    @Mapping(target = "enrollmentId", source = "id")
    MyCourseResponse toMyCourseResponse(Enrollment enrollment);

    @Mapping(target = "sectionId", source = "section.id")
    @Mapping(target = "sectionTitle", source = "section.title")
    LessonRef toLessonRef(Lesson lesson);
}
