package com.pisey.elearning.service;

import java.util.UUID;

/**
 * Keycloak Admin REST API calls made with the {@code elearning-backend} service account.
 * Ids are Keycloak user ids ({@code users.keycloak_id}).
 */
public interface KeycloakAdminService {

    /** Updates only the non-null values. */
    void updateUser(UUID userId, String firstName, String lastName, String email);

    void resetPassword(UUID userId, String newPassword);

    void addRealmRole(UUID userId, String roleName);

    void removeRealmRole(UUID userId, String roleName);

    void setEnabled(UUID userId, boolean enabled);

    /** True if Keycloak accepts the username/email and password (password grant, dev-only client). */
    boolean verifyPassword(String username, String password);
}
