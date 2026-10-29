package com.pisey.elearning.mapper;

import com.pisey.elearning.dto.review.ReviewResponse;
import com.pisey.elearning.entity.Review;
import com.pisey.elearning.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReviewMapper {

    @Mapping(target = "courseId", source = "course.id")
    @Mapping(target = "author", source = "student")
    ReviewResponse toResponse(Review review);

    ReviewResponse.Author toAuthor(User student);
}
