package com.pisey.elearning.mapper;

import com.pisey.elearning.dto.content.CourseOutlineResponse.LessonOutline;
import com.pisey.elearning.dto.content.LessonResourceResponse;
import com.pisey.elearning.dto.content.LessonResponse;
import com.pisey.elearning.dto.content.SectionResponse;
import com.pisey.elearning.entity.Lesson;
import com.pisey.elearning.entity.LessonResource;
import com.pisey.elearning.entity.Section;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ContentMapper {

    @Mapping(target = "courseId", source = "course.id")
    SectionResponse toSectionResponse(Section section);

    @Mapping(target = "courseId", source = "section.course.id")
    @Mapping(target = "sectionId", source = "section.id")
    LessonResponse toLessonResponse(Lesson lesson);

    LessonResourceResponse toResourceResponse(LessonResource resource);

    /** Outline row: no content fields by construction. */
    LessonOutline toLessonOutline(Lesson lesson, boolean hasQuiz);
}
