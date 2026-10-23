package com.pisey.elearning.service.impl;

import com.pisey.elearning.exception.BadRequestException;
import com.pisey.elearning.exception.ConflictException;
import com.pisey.elearning.exception.ExternalServiceException;
import com.pisey.elearning.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KeycloakAdminServiceImplTest {

    private static final String ADMIN = "http://kc/admin/realms/elearning";
    private static final String OIDC = "http://kc/realms/elearning/protocol/openid-connect";
    private static final UUID USER_ID = UUID.fromString("9c2612de-0000-0000-0000-000000000001");

    private MockRestServiceServer adminServer;
    private MockRestServiceServer tokenServer;
    private KeycloakAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        RestClient.Builder adminBuilder = RestClient.builder().baseUrl(ADMIN);
        RestClient.Builder tokenBuilder = RestClient.builder().baseUrl(OIDC);
        adminServer = MockRestServiceServer.bindTo(adminBuilder).build();
        tokenServer = MockRestServiceServer.bindTo(tokenBuilder).build();
        service = new KeycloakAdminServiceImpl(adminBuilder.build(), tokenBuilder.build());
    }

    @Test
    void updateUserSendsOnlyNonNullFields() {
        adminServer.expect(requestTo(ADMIN + "/users/" + USER_ID))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("{\"firstName\":\"Vanna\",\"lastName\":\"Kim\"}", true))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        service.updateUser(USER_ID, "Vanna", "Kim", null);

        adminServer.verify();
    }

    @Test
    void updateUserWithNothingToChangeMakesNoCall() {
        service.updateUser(USER_ID, null, null, null);

        adminServer.verify();
    }

    @Test
    void updateUserMapsKeycloakConflictTo409() {
        adminServer.expect(requestTo(ADMIN + "/users/" + USER_ID))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> service.updateUser(USER_ID, null, null, "taken@elearning.local"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void unknownUserMapsTo404() {
        adminServer.expect(requestTo(ADMIN + "/users/" + USER_ID))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.setEnabled(USER_ID, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void serverErrorMapsToExternalServiceException() {
        adminServer.expect(requestTo(ADMIN + "/users/" + USER_ID + "/reset-password"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> service.resetPassword(USER_ID, "NewPass@123"))
                .isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void keycloakValidationErrorMapsTo400WithItsDescription() {
        adminServer.expect(requestTo(ADMIN + "/users/" + USER_ID + "/reset-password"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalidPasswordMinLengthMessage\",\"error_description\":\"Invalid password: minimum length 12.\"}"));

        assertThatThrownBy(() -> service.resetPassword(USER_ID, "NewPass@123"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("minimum length 12");
    }

    @Test
    void resetPasswordSendsNonTemporaryCredential() {
        adminServer.expect(requestTo(ADMIN + "/users/" + USER_ID + "/reset-password"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("{\"type\":\"password\",\"value\":\"NewPass@123\",\"temporary\":false}", true))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        service.resetPassword(USER_ID, "NewPass@123");

        adminServer.verify();
    }

    @Test
    void addRealmRoleLooksUpRoleThenPostsIt() {
        String role = "{\"id\":\"r-1\",\"name\":\"INSTRUCTOR\"}";
        adminServer.expect(requestTo(ADMIN + "/roles/INSTRUCTOR"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(role, MediaType.APPLICATION_JSON));
        adminServer.expect(requestTo(ADMIN + "/users/" + USER_ID + "/role-mappings/realm"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("[" + role + "]", true))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        service.addRealmRole(USER_ID, "INSTRUCTOR");

        adminServer.verify();
    }

    @Test
    void removeRealmRoleDeletesWithRoleBody() {
        String role = "{\"id\":\"r-1\",\"name\":\"INSTRUCTOR\"}";
        adminServer.expect(requestTo(ADMIN + "/roles/INSTRUCTOR"))
                .andRespond(withSuccess(role, MediaType.APPLICATION_JSON));
        adminServer.expect(requestTo(ADMIN + "/users/" + USER_ID + "/role-mappings/realm"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(content().json("[" + role + "]", true))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        service.removeRealmRole(USER_ID, "INSTRUCTOR");

        adminServer.verify();
    }

    @Test
    void unknownRoleMapsTo404() {
        adminServer.expect(requestTo(ADMIN + "/roles/TEACHER"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.addRealmRole(USER_ID, "TEACHER"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void verifyPasswordTrueWhenKeycloakIssuesToken() {
        tokenServer.expect(requestTo(OIDC + "/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(Map.of(
                        "grant_type", "password",
                        "client_id", "elearning-password-check",
                        "username", "student1@elearning.local")))
                .andRespond(withSuccess("{\"access_token\":\"x\"}", MediaType.APPLICATION_JSON));

        assertThat(service.verifyPassword("student1@elearning.local", "Student@123")).isTrue();
    }

    @Test
    void verifyPasswordFalseOnInvalidGrant() {
        tokenServer.expect(requestTo(OIDC + "/token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid_grant\"}"));

        assertThat(service.verifyPassword("student1@elearning.local", "wrong")).isFalse();
    }
}
