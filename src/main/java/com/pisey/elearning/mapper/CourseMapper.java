package com.pisey.elearning.mapper;

import com.pisey.elearning.dto.course.CourseSummaryResponse;
import com.pisey.elearning.entity.Course;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CourseMapper {

    @Mapping(target = "instructorName", source = "instructor.fullName")
    @Mapping(target = "categoryName", source = "category.name")
    CourseSummaryResponse toSummaryResponse(Course course);
}
