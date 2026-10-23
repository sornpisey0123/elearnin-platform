package com.pisey.elearning.service.impl;

import com.pisey.elearning.exception.BadRequestException;
import com.pisey.elearning.exception.ConflictException;
import com.pisey.elearning.exception.ExternalServiceException;
import com.pisey.elearning.exception.ResourceNotFoundException;
import com.pisey.elearning.service.KeycloakAdminService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class KeycloakAdminServiceImpl implements KeycloakAdminService {

    private static final String PASSWORD_CHECK_CLIENT_ID = "elearning-password-check";
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
    };

    private final RestClient adminClient;
    private final RestClient tokenClient;

    public KeycloakAdminServiceImpl(@Qualifier("keycloakAdminRestClient") RestClient adminClient,
                                    @Qualifier("keycloakTokenRestClient") RestClient tokenClient) {
        this.adminClient = adminClient;
        this.tokenClient = tokenClient;
    }

    @Override
    public void updateUser(UUID userId, String firstName, String lastName, String email) {
        Map<String, Object> body = new LinkedHashMap<>();
        putIfNotNull(body, "firstName", firstName);
        putIfNotNull(body, "lastName", lastName);
        putIfNotNull(body, "email", email);
        if (body.isEmpty()) {
            return;
        }
        call(userId, () -> adminClient.put()
                .uri("/users/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public void resetPassword(UUID userId, String newPassword) {
        call(userId, () -> adminClient.put()
                .uri("/users/{id}/reset-password", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("type", "password", "value", newPassword, "temporary", false))
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public void addRealmRole(UUID userId, String roleName) {
        Map<String, Object> role = getRealmRole(roleName);
        call(userId, () -> adminClient.post()
                .uri("/users/{id}/role-mappings/realm", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(role))
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public void removeRealmRole(UUID userId, String roleName) {
        Map<String, Object> role = getRealmRole(roleName);
        // DELETE with a body: Keycloak expects the role representations to remove
        call(userId, () -> adminClient.method(HttpMethod.DELETE)
                .uri("/users/{id}/role-mappings/realm", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(role))
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public void setEnabled(UUID userId, boolean enabled) {
        call(userId, () -> adminClient.put()
                .uri("/users/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("enabled", enabled))
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public boolean verifyPassword(String username, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", PASSWORD_CHECK_CLIENT_ID);
        form.add("username", username);
        form.add("password", password);
        try {
            tokenClient.post()
                    .uri("/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException ex) {
            // Keycloak answers 401 invalid_grant for a wrong password
            if (ex.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)
                    || ex.getStatusCode().isSameCodeAs(HttpStatus.BAD_REQUEST)) {
                return false;
            }
            throw new ExternalServiceException("Keycloak password check failed: " + ex.getStatusCode(), ex);
        } catch (RestClientException ex) {
            throw new ExternalServiceException("Keycloak is unreachable", ex);
        }
    }

    private Map<String, Object> getRealmRole(String roleName) {
        try {
            return adminClient.get()
                    .uri("/roles/{name}", roleName)
                    .retrieve()
                    .body(JSON_OBJECT);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                throw new ResourceNotFoundException("Keycloak realm role", "name", roleName);
            }
            throw new ExternalServiceException("Keycloak role lookup failed: " + ex.getStatusCode(), ex);
        } catch (RestClientException ex) {
            throw new ExternalServiceException("Keycloak is unreachable", ex);
        }
    }

    /** Runs an Admin API call, translating Keycloak errors into the API's exceptions. */
    private static void call(UUID userId, Supplier<?> request) {
        try {
            request.get();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                throw new ResourceNotFoundException("Keycloak user", "id", userId);
            }
            if (ex.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT)) {
                throw new ConflictException("Keycloak rejected the change: email or username already in use");
            }
            if (ex.getStatusCode().isSameCodeAs(HttpStatus.BAD_REQUEST)) {
                // e.g. password policy or user-profile validation; Keycloak explains it in error_description
                throw new BadRequestException("Keycloak rejected the change: " + errorDescription(ex));
            }
            throw new ExternalServiceException("Keycloak Admin API call failed: " + ex.getStatusCode(), ex);
        } catch (RestClientException ex) {
            throw new ExternalServiceException("Keycloak is unreachable", ex);
        }
    }

    private static String errorDescription(RestClientResponseException ex) {
        try {
            Map<String, Object> body = ex.getResponseBodyAs(JSON_OBJECT);
            if (body != null) {
                Object description = body.getOrDefault("error_description", body.get("errorMessage"));
                if (description == null) {
                    description = body.get("error");
                }
                if (description != null) {
                    return description.toString();
                }
            }
        } catch (RuntimeException ignored) {
            // Non-JSON body; fall through
        }
        return ex.getStatusText();
    }

    private static void putIfNotNull(Map<String, Object> body, String key, Object value) {
        if (value != null) {
            body.put(key, value);
        }
    }
}
