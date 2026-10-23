package com.pisey.elearning.mapper;

import com.pisey.elearning.dto.user.ProfileResponse;
import com.pisey.elearning.dto.user.UserResponse;
import com.pisey.elearning.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    ProfileResponse toProfileResponse(User user);

    UserResponse toResponse(User user);
}
