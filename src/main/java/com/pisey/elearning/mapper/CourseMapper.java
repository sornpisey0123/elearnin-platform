package com.pisey.elearning.mapper;

import com.pisey.elearning.dto.course.CourseDetailResponse;
import com.pisey.elearning.dto.course.CourseSummaryResponse;
import com.pisey.elearning.dto.course.InstructorCourseResponse;
import com.pisey.elearning.entity.Course;
import com.pisey.elearning.repository.projection.CourseContentStats;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CourseMapper {

    @Mapping(target = "instructorName", source = "instructor.fullName")
    @Mapping(target = "categoryName", source = "category.name")
    CourseSummaryResponse toSummaryResponse(Course course);

    @Mapping(target = "categoryName", source = "category.name")
    InstructorCourseResponse toInstructorResponse(Course course);

    @Mapping(target = "id", source = "course.id")
    @Mapping(target = "createdAt", source = "course.createdAt")
    @Mapping(target = "updatedAt", source = "course.updatedAt")
    CourseDetailResponse toDetailResponse(Course course, CourseContentStats stats);
}
