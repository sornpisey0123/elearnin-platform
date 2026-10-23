package com.pisey.elearning.service;

import com.pisey.elearning.dto.common.PageResponse;
import com.pisey.elearning.dto.user.PasswordChangeRequest;
import com.pisey.elearning.dto.user.ProfileResponse;
import com.pisey.elearning.dto.user.ProfileUpdateRequest;
import com.pisey.elearning.dto.user.UserResponse;
import com.pisey.elearning.entity.enums.Role;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {

    // --- /api/me: the caller's own account ---

    ProfileResponse getProfile(Jwt jwt);

    /** Updates locally and in Keycloak; a Keycloak failure rolls back the local change. */
    ProfileResponse updateProfile(Jwt jwt, ProfileUpdateRequest request);

    /** jpg/png/webp up to 2 MB; the previous avatar file is deleted once the change commits. */
    ProfileResponse updateAvatar(Jwt jwt, MultipartFile file);

    void changePassword(Jwt jwt, PasswordChangeRequest request);

    // --- /api/admin/users ---

    PageResponse<UserResponse> findAll(Role role, String keyword, Pageable pageable);

    /** Admins cannot change their own role. */
    UserResponse changeRole(Long id, Role role, Jwt caller);

    /** Admins cannot disable themselves. */
    UserResponse changeStatus(Long id, boolean active, Jwt caller);
}
