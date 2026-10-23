package com.pisey.elearning.service.impl;

import com.pisey.elearning.entity.User;
import com.pisey.elearning.entity.enums.Role;
import com.pisey.elearning.exception.BadRequestException;
import com.pisey.elearning.exception.ForbiddenException;
import com.pisey.elearning.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceImplTest {

    private static final UUID KEYCLOAK_ID = UUID.randomUUID();

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CurrentUserServiceImpl currentUserService;

    @Test
    void createsUserOnFirstRequestWithHighestRole() {
        given(userRepository.findByKeycloakId(KEYCLOAK_ID)).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        User user = currentUserService.getOrCreate(jwt(b -> b
                .claim("email", "Instructor1@Elearning.local")
                .claim("name", "Dara Chan")
                .claim("realm_access", Map.of("roles", List.of("STUDENT", "INSTRUCTOR", "offline_access")))));

        assertThat(user.getKeycloakId()).isEqualTo(KEYCLOAK_ID);
        assertThat(user.getRole()).isEqualTo(Role.INSTRUCTOR);
        assertThat(user.getEmail()).isEqualTo("instructor1@elearning.local");
        assertThat(user.getFullName()).isEqualTo("Dara Chan");
        assertThat(user.isActive()).isTrue();
    }

    @Test
    void adminWinsOverOtherRoles() {
        given(userRepository.findByKeycloakId(KEYCLOAK_ID)).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        User user = currentUserService.getOrCreate(jwt(b -> b
                .claim("email", "admin@elearning.local")
                .claim("name", "Platform Admin")
                .claim("realm_access", Map.of("roles", List.of("STUDENT", "ADMIN", "INSTRUCTOR")))));

        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void defaultsToStudentWhenTokenHasNoAppRole() {
        given(userRepository.findByKeycloakId(KEYCLOAK_ID)).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        User user = currentUserService.getOrCreate(jwt(b -> b
                .claim("email", "new@elearning.local")
                .claim("name", "New User")));

        assertThat(user.getRole()).isEqualTo(Role.STUDENT);
    }

    @Test
    void syncsExistingUserWithoutInsertingAgain() {
        User existing = User.builder()
                .keycloakId(KEYCLOAK_ID)
                .role(Role.STUDENT)
                .email("old@elearning.local")
                .fullName("Old Name")
                .build();
        given(userRepository.findByKeycloakId(KEYCLOAK_ID)).willReturn(Optional.of(existing));

        User user = currentUserService.getOrCreate(jwt(b -> b
                .claim("email", "new@elearning.local")
                .claim("name", "New Name")
                .claim("realm_access", Map.of("roles", List.of("INSTRUCTOR")))));

        assertThat(user).isSameAs(existing);
        assertThat(user.getRole()).isEqualTo(Role.INSTRUCTOR);
        assertThat(user.getEmail()).isEqualTo("new@elearning.local");
        assertThat(user.getFullName()).isEqualTo("New Name");
        verify(userRepository, never()).save(any());
    }

    @Test
    void tokenIssuedBeforeLastLocalChangeDoesNotOverwriteIt() {
        User existing = User.builder()
                .keycloakId(KEYCLOAK_ID)
                .role(Role.INSTRUCTOR)
                .email("new@elearning.local")
                .fullName("Edited Name")
                .build();
        existing.setUpdatedAt(Instant.now());
        given(userRepository.findByKeycloakId(KEYCLOAK_ID)).willReturn(Optional.of(existing));

        // Token minted a minute before the profile edit / role change still carries the old values
        User user = currentUserService.getOrCreate(jwt(b -> b
                .issuedAt(Instant.now().minusSeconds(60))
                .claim("email", "old@elearning.local")
                .claim("name", "Old Name")
                .claim("realm_access", Map.of("roles", List.of("STUDENT")))));

        assertThat(user.getFullName()).isEqualTo("Edited Name");
        assertThat(user.getEmail()).isEqualTo("new@elearning.local");
        assertThat(user.getRole()).isEqualTo(Role.INSTRUCTOR);
    }

    @Test
    void disabledUserIsRejectedWhileOldTokenIsStillValid() {
        User existing = User.builder()
                .keycloakId(KEYCLOAK_ID)
                .role(Role.STUDENT)
                .email("s@elearning.local")
                .fullName("Disabled User")
                .active(false)
                .build();
        given(userRepository.findByKeycloakId(KEYCLOAK_ID)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> currentUserService.getOrCreate(jwt(b -> b
                .claim("email", "s@elearning.local")
                .claim("name", "Disabled User"))))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void buildsNameFromGivenAndFamilyNameWhenNameClaimMissing() {
        given(userRepository.findByKeycloakId(KEYCLOAK_ID)).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        User user = currentUserService.getOrCreate(jwt(b -> b
                .claim("email", "s@elearning.local")
                .claim("given_name", "Rith")
                .claim("family_name", "Sok")));

        assertThat(user.getFullName()).isEqualTo("Rith Sok");
    }

    @Test
    void fallsBackToUsernameWhenNoNameClaims() {
        given(userRepository.findByKeycloakId(KEYCLOAK_ID)).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        User user = currentUserService.getOrCreate(jwt(b -> b
                .claim("email", "s@elearning.local")
                .claim("preferred_username", "student2")));

        assertThat(user.getFullName()).isEqualTo("student2");
    }

    @Test
    void rejectsTokenWithoutEmail() {
        assertThatThrownBy(() -> currentUserService.getOrCreate(jwt(b -> b.claim("name", "No Email"))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("email");
        verify(userRepository, never()).save(any());
    }

    private static Jwt jwt(Consumer<Jwt.Builder> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(KEYCLOAK_ID.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        claims.accept(builder);
        return builder.build();
    }
}
