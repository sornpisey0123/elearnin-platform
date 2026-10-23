package com.pisey.elearning.service.impl;

import com.pisey.elearning.entity.User;
import com.pisey.elearning.entity.enums.Role;
import com.pisey.elearning.exception.BadRequestException;
import com.pisey.elearning.exception.ForbiddenException;
import com.pisey.elearning.repository.UserRepository;
import com.pisey.elearning.service.CurrentUserService;
import com.pisey.elearning.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurrentUserServiceImpl implements CurrentUserService {

    private static final int FULL_NAME_MAX_LENGTH = 120;

    /** Highest first: a user with several realm roles gets the most privileged one. */
    private static final List<Role> ROLE_PRIORITY = List.of(Role.ADMIN, Role.INSTRUCTOR, Role.STUDENT);

    private final UserRepository userRepository;

    @Override
    @Transactional
    public User getOrCreate(Jwt jwt) {
        UUID keycloakId = SecurityUtils.keycloakId(jwt);
        Role role = resolveRole(jwt);
        String email = resolveEmail(jwt);
        String fullName = resolveFullName(jwt);

        User user = userRepository.findByKeycloakId(keycloakId)
                .orElse(null);
        if (user == null) {
            return userRepository.save(User.builder()
                    .keycloakId(keycloakId)
                    .role(role)
                    .email(email)
                    .fullName(fullName)
                    .build());
        }
        if (!user.isActive()) {
            // Keycloak blocks new logins; this closes the window until the current token expires
            throw new ForbiddenException("Your account is disabled");
        }
        if (isNewerThanLocalChanges(jwt, user)) {
            // Dirty checking only issues an UPDATE when a value actually changed
            user.setRole(role);
            user.setEmail(email);
            user.setFullName(fullName);
        }
        return user;
    }

    /**
     * A token issued before the row was last updated (profile edit, admin role change) carries stale
     * values and must not overwrite them. {@code iat} has second precision, so a token from the same
     * second is treated as stale; the next token refresh syncs it.
     */
    private static boolean isNewerThanLocalChanges(Jwt jwt, User user) {
        Instant issuedAt = jwt.getIssuedAt();
        Instant updatedAt = user.getUpdatedAt();
        return issuedAt == null || updatedAt == null || issuedAt.isAfter(updatedAt);
    }

    private static Role resolveRole(Jwt jwt) {
        List<String> realmRoles = SecurityUtils.realmRoles(jwt);
        return ROLE_PRIORITY.stream()
                .filter(role -> realmRoles.contains(role.name()))
                .findFirst()
                .orElse(Role.STUDENT);
    }

    private static String resolveEmail(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (!StringUtils.hasText(email)) {
            throw new BadRequestException("Token has no email claim; add the 'email' scope in Keycloak");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String resolveFullName(Jwt jwt) {
        String name = jwt.getClaimAsString("name");
        if (!StringUtils.hasText(name)) {
            name = String.join(" ",
                    nullToEmpty(jwt.getClaimAsString("given_name")),
                    nullToEmpty(jwt.getClaimAsString("family_name"))).trim();
        }
        if (!StringUtils.hasText(name)) {
            name = jwt.getClaimAsString("preferred_username");
        }
        if (!StringUtils.hasText(name)) {
            name = jwt.getClaimAsString("email");
        }
        name = name.trim();
        return name.length() > FULL_NAME_MAX_LENGTH ? name.substring(0, FULL_NAME_MAX_LENGTH) : name;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
