package com.pisey.elearning.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SecurityUtils {

    public static final String ROLE_PREFIX = "ROLE_";

    private SecurityUtils() {
    }

    /** Keycloak puts realm roles in {@code realm_access.roles}; returns them as-is (e.g. "ADMIN"). */
    public static List<String> realmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .map(String::valueOf)
                .toList();
    }

    /** The Keycloak user id ({@code sub} claim). */
    public static UUID keycloakId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    /** The JWT of the current request, if the caller is authenticated with a bearer token. */
    public static Optional<Jwt> currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return Optional.of(jwt);
        }
        return Optional.empty();
    }

    /** True if the current caller has the given role, e.g. {@code hasRole("ADMIN")}. */
    public static boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        String authority = ROLE_PREFIX + role;
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }

    public static boolean isAdmin() {
        return hasRole("ADMIN");
    }
}
