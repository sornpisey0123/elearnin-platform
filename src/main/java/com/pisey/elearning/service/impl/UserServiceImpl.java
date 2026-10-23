package com.pisey.elearning.service.impl;

import com.pisey.elearning.dto.common.PageResponse;
import com.pisey.elearning.dto.user.PasswordChangeRequest;
import com.pisey.elearning.dto.user.ProfileResponse;
import com.pisey.elearning.dto.user.ProfileUpdateRequest;
import com.pisey.elearning.dto.user.UserResponse;
import com.pisey.elearning.entity.User;
import com.pisey.elearning.entity.enums.Role;
import com.pisey.elearning.exception.BadRequestException;
import com.pisey.elearning.exception.ConflictException;
import com.pisey.elearning.exception.ForbiddenException;
import com.pisey.elearning.exception.ResourceNotFoundException;
import com.pisey.elearning.mapper.UserMapper;
import com.pisey.elearning.repository.UserRepository;
import com.pisey.elearning.repository.specification.UserSpecifications;
import com.pisey.elearning.service.CurrentUserService;
import com.pisey.elearning.service.FileStorageService;
import com.pisey.elearning.service.KeycloakAdminService;
import com.pisey.elearning.service.UserService;
import com.pisey.elearning.utils.FileTransactionUtils;
import com.pisey.elearning.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    static final String AVATAR_FOLDER = "avatars";
    static final Set<String> AVATAR_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    static final long AVATAR_MAX_BYTES = 2L * 1024 * 1024;

    /** Realm roles granted directly. STUDENT comes from the realm's default role, so it is never removed. */
    private static final List<Role> ELEVATED_ROLES = List.of(Role.ADMIN, Role.INSTRUCTOR);

    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final KeycloakAdminService keycloakAdminService;
    private final FileStorageService fileStorageService;
    private final UserMapper userMapper;

    // getOrCreate may insert the user on first request, so even the read needs a writable transaction
    @Override
    @Transactional
    public ProfileResponse getProfile(Jwt jwt) {
        return userMapper.toProfileResponse(currentUserService.getOrCreate(jwt));
    }

    @Override
    @Transactional
    public ProfileResponse updateProfile(Jwt jwt, ProfileUpdateRequest request) {
        User user = currentUserService.getOrCreate(jwt);
        String fullName = request.fullName().trim();
        String email = StringUtils.hasText(request.email()) ? request.email().trim().toLowerCase(Locale.ROOT) : null;

        if (email != null && userRepository.existsByEmailIgnoreCaseAndIdNot(email, user.getId())) {
            throw new ConflictException("Email already in use: " + email);
        }

        user.setFullName(fullName);
        user.setBio(StringUtils.hasText(request.bio()) ? request.bio().trim() : null);
        if (email != null) {
            user.setEmail(email);
        }
        // Flush first so local constraint errors surface before Keycloak is changed;
        // a Keycloak failure afterwards rolls this back
        userRepository.saveAndFlush(user);

        String[] names = splitFullName(fullName);
        keycloakAdminService.updateUser(user.getKeycloakId(), names[0], names[1], email);
        return userMapper.toProfileResponse(user);
    }

    @Override
    @Transactional
    public ProfileResponse updateAvatar(Jwt jwt, MultipartFile file) {
        User user = currentUserService.getOrCreate(jwt);
        String oldUrl = user.getAvatarUrl();
        String newUrl = fileStorageService.store(file, AVATAR_FOLDER, AVATAR_TYPES, AVATAR_MAX_BYTES);

        user.setAvatarUrl(newUrl);
        userRepository.saveAndFlush(user);

        FileTransactionUtils.replaceAfterCompletion(fileStorageService, oldUrl, newUrl);
        return userMapper.toProfileResponse(user);
    }

    @Override
    @Transactional
    public void changePassword(Jwt jwt, PasswordChangeRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BadRequestException("New password and confirmation do not match");
        }
        if (request.newPassword().equals(request.currentPassword())) {
            throw new BadRequestException("New password must be different from the current password");
        }

        User user = currentUserService.getOrCreate(jwt);
        String username = StringUtils.hasText(jwt.getClaimAsString("preferred_username"))
                ? jwt.getClaimAsString("preferred_username")
                : user.getEmail();
        if (!keycloakAdminService.verifyPassword(username, request.currentPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }
        keycloakAdminService.resetPassword(user.getKeycloakId(), request.newPassword());
    }

    @Override
    public PageResponse<UserResponse> findAll(Role role, String keyword, Pageable pageable) {
        Specification<User> spec = Specification.allOf(
                UserSpecifications.hasRole(role),
                UserSpecifications.keywordMatches(keyword));
        return PageResponse.from(userRepository.findAll(spec, pageable), userMapper::toResponse);
    }

    @Override
    @Transactional
    public UserResponse changeRole(Long id, Role role, Jwt caller) {
        User user = getUser(id);
        if (isSelf(user, caller)) {
            throw new ForbiddenException("You cannot change your own role");
        }
        if (user.getRole() == role) {
            return userMapper.toResponse(user);
        }

        user.setRole(role);
        userRepository.saveAndFlush(user);

        UUID keycloakId = user.getKeycloakId();
        if (ELEVATED_ROLES.contains(role)) {
            keycloakAdminService.addRealmRole(keycloakId, role.name());
        }
        for (Role other : ELEVATED_ROLES) {
            if (other != role) {
                keycloakAdminService.removeRealmRole(keycloakId, other.name());
            }
        }
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse changeStatus(Long id, boolean active, Jwt caller) {
        User user = getUser(id);
        if (isSelf(user, caller)) {
            throw new ForbiddenException("You cannot change your own status");
        }
        if (user.isActive() == active) {
            return userMapper.toResponse(user);
        }

        user.setActive(active);
        userRepository.saveAndFlush(user);
        keycloakAdminService.setEnabled(user.getKeycloakId(), active);
        return userMapper.toResponse(user);
    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    private static boolean isSelf(User user, Jwt caller) {
        return user.getKeycloakId().equals(SecurityUtils.keycloakId(caller));
    }

    /** "Mary Ann Smith" → ["Mary Ann", "Smith"]; a single word becomes the first name with an empty last name. */
    static String[] splitFullName(String fullName) {
        int lastSpace = fullName.lastIndexOf(' ');
        if (lastSpace < 0) {
            return new String[]{fullName, ""};
        }
        return new String[]{fullName.substring(0, lastSpace).trim(), fullName.substring(lastSpace + 1)};
    }
}
