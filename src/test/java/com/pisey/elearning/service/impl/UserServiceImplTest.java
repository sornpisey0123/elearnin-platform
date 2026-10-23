package com.pisey.elearning.service.impl;

import com.pisey.elearning.dto.user.PasswordChangeRequest;
import com.pisey.elearning.dto.user.ProfileResponse;
import com.pisey.elearning.dto.user.ProfileUpdateRequest;
import com.pisey.elearning.dto.user.UserResponse;
import com.pisey.elearning.entity.User;
import com.pisey.elearning.entity.enums.Role;
import com.pisey.elearning.exception.BadRequestException;
import com.pisey.elearning.exception.ConflictException;
import com.pisey.elearning.exception.ExternalServiceException;
import com.pisey.elearning.exception.ForbiddenException;
import com.pisey.elearning.exception.ResourceNotFoundException;
import com.pisey.elearning.mapper.UserMapper;
import com.pisey.elearning.mapper.UserMapperImpl;
import com.pisey.elearning.repository.UserRepository;
import com.pisey.elearning.service.CurrentUserService;
import com.pisey.elearning.service.FileStorageService;
import com.pisey.elearning.service.KeycloakAdminService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final UUID ME = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Mock
    private UserRepository userRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private KeycloakAdminService keycloakAdminService;
    @Mock
    private FileStorageService fileStorageService;

    private final UserMapper userMapper = new UserMapperImpl();

    private UserServiceImpl userService;
    private User me;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository, currentUserService, keycloakAdminService,
                fileStorageService, userMapper);
        me = user(1L, ME, Role.STUDENT, "student1@elearning.local", "Vanna Kim");
        jwt = jwt(ME);
    }

    @Nested
    class Profile {

        @Test
        void getProfileReturnsCurrentUser() {
            given(currentUserService.getOrCreate(jwt)).willReturn(me);

            ProfileResponse profile = userService.getProfile(jwt);

            assertThat(profile.id()).isEqualTo(1L);
            assertThat(profile.email()).isEqualTo("student1@elearning.local");
            assertThat(profile.role()).isEqualTo(Role.STUDENT);
        }

        @Test
        void updateProfileSavesLocallyThenSyncsSplitNameAndEmailToKeycloak() {
            given(currentUserService.getOrCreate(jwt)).willReturn(me);
            given(userRepository.existsByEmailIgnoreCaseAndIdNot("vanna@elearning.local", 1L)).willReturn(false);

            ProfileResponse profile = userService.updateProfile(jwt,
                    new ProfileUpdateRequest("  Vanna Mary Kim ", " Loves Java ", " Vanna@Elearning.local "));

            assertThat(profile.fullName()).isEqualTo("Vanna Mary Kim");
            assertThat(profile.bio()).isEqualTo("Loves Java");
            assertThat(profile.email()).isEqualTo("vanna@elearning.local");
            verify(userRepository).saveAndFlush(me);
            verify(keycloakAdminService).updateUser(ME, "Vanna Mary", "Kim", "vanna@elearning.local");
        }

        @Test
        void updateProfileWithoutEmailKeepsCurrentEmail() {
            given(currentUserService.getOrCreate(jwt)).willReturn(me);

            ProfileResponse profile = userService.updateProfile(jwt, new ProfileUpdateRequest("Vanna", null, " "));

            assertThat(profile.email()).isEqualTo("student1@elearning.local");
            assertThat(profile.bio()).isNull();
            verify(keycloakAdminService).updateUser(ME, "Vanna", "", null);
        }

        @Test
        void updateProfileRejectsEmailOfAnotherUser() {
            given(currentUserService.getOrCreate(jwt)).willReturn(me);
            given(userRepository.existsByEmailIgnoreCaseAndIdNot("taken@elearning.local", 1L)).willReturn(true);

            assertThatThrownBy(() -> userService.updateProfile(jwt,
                    new ProfileUpdateRequest("Vanna Kim", null, "taken@elearning.local")))
                    .isInstanceOf(ConflictException.class);
            verify(userRepository, never()).saveAndFlush(any());
            verifyNoInteractions(keycloakAdminService);
        }

        @Test
        void keycloakFailurePropagatesSoTheTransactionRollsBack() {
            given(currentUserService.getOrCreate(jwt)).willReturn(me);
            willThrow(new ExternalServiceException("down", null))
                    .given(keycloakAdminService).updateUser(any(), anyString(), anyString(), any());

            assertThatThrownBy(() -> userService.updateProfile(jwt, new ProfileUpdateRequest("New Name", null, null)))
                    .isInstanceOf(ExternalServiceException.class);
        }

        @Test
        void splitFullNameUsesLastWordAsLastName() {
            assertThat(UserServiceImpl.splitFullName("Mary Ann Smith")).containsExactly("Mary Ann", "Smith");
            assertThat(UserServiceImpl.splitFullName("Dara Chan")).containsExactly("Dara", "Chan");
            assertThat(UserServiceImpl.splitFullName("Madonna")).containsExactly("Madonna", "");
        }
    }

    @Nested
    class Avatar {

        private final MockMultipartFile file = new MockMultipartFile("file", "me.png", "image/png", new byte[]{1});

        @BeforeEach
        void startSynchronization() {
            TransactionSynchronizationManager.initSynchronization();
        }

        @AfterEach
        void clearSynchronization() {
            TransactionSynchronizationManager.clearSynchronization();
        }

        @Test
        void storesNewAvatarAndDeletesOldOneAfterCommit() {
            me.setAvatarUrl("/files/avatars/old.png");
            given(currentUserService.getOrCreate(jwt)).willReturn(me);
            given(fileStorageService.store(file, "avatars", UserServiceImpl.AVATAR_TYPES, UserServiceImpl.AVATAR_MAX_BYTES))
                    .willReturn("/files/avatars/new.png");

            ProfileResponse profile = userService.updateAvatar(jwt, file);
            assertThat(profile.avatarUrl()).isEqualTo("/files/avatars/new.png");
            verify(fileStorageService, never()).delete(anyString());

            completeTransaction(TransactionSynchronization.STATUS_COMMITTED);
            verify(fileStorageService).delete("/files/avatars/old.png");
        }

        @Test
        void deletesNewFileWhenTransactionRollsBack() {
            me.setAvatarUrl("/files/avatars/old.png");
            given(currentUserService.getOrCreate(jwt)).willReturn(me);
            given(fileStorageService.store(any(), anyString(), any(), eq(UserServiceImpl.AVATAR_MAX_BYTES)))
                    .willReturn("/files/avatars/new.png");

            userService.updateAvatar(jwt, file);
            completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

            verify(fileStorageService).delete("/files/avatars/new.png");
            verify(fileStorageService, never()).delete("/files/avatars/old.png");
        }

        @Test
        void invalidFileIsRejectedByStorage() {
            given(currentUserService.getOrCreate(jwt)).willReturn(me);
            given(fileStorageService.store(any(), anyString(), any(), eq(UserServiceImpl.AVATAR_MAX_BYTES)))
                    .willThrow(new BadRequestException("Unsupported file type"));

            assertThatThrownBy(() -> userService.updateAvatar(jwt, file)).isInstanceOf(BadRequestException.class);
            assertThat(me.getAvatarUrl()).isNull();
        }

        private void completeTransaction(int status) {
            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(status));
        }
    }

    @Nested
    class Password {

        @Test
        void verifiesCurrentPasswordWithUsernameThenResets() {
            given(currentUserService.getOrCreate(jwt)).willReturn(me);
            given(keycloakAdminService.verifyPassword("student1", "Student@123")).willReturn(true);

            userService.changePassword(jwt, new PasswordChangeRequest("Student@123", "NewPass@123", "NewPass@123"));

            verify(keycloakAdminService).resetPassword(ME, "NewPass@123");
        }

        @Test
        void rejectsConfirmationMismatch() {
            assertThatThrownBy(() -> userService.changePassword(jwt,
                    new PasswordChangeRequest("Student@123", "NewPass@123", "Different@123")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("do not match");
            verifyNoInteractions(keycloakAdminService);
        }

        @Test
        void rejectsNewPasswordEqualToCurrent() {
            assertThatThrownBy(() -> userService.changePassword(jwt,
                    new PasswordChangeRequest("Student@123", "Student@123", "Student@123")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("different");
            verifyNoInteractions(keycloakAdminService);
        }

        @Test
        void rejectsWrongCurrentPasswordWithoutResetting() {
            given(currentUserService.getOrCreate(jwt)).willReturn(me);
            given(keycloakAdminService.verifyPassword("student1", "wrong")).willReturn(false);

            assertThatThrownBy(() -> userService.changePassword(jwt,
                    new PasswordChangeRequest("wrong", "NewPass@123", "NewPass@123")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("incorrect");
            verify(keycloakAdminService, never()).resetPassword(any(), anyString());
        }
    }

    @Nested
    class AdminRole {

        private User target;

        @BeforeEach
        void setUpTarget() {
            target = user(2L, OTHER, Role.STUDENT, "student2@elearning.local", "Rith Sok");
        }

        @Test
        void promoteToInstructorAddsRoleAndRemovesAdmin() {
            given(userRepository.findById(2L)).willReturn(Optional.of(target));

            UserResponse response = userService.changeRole(2L, Role.INSTRUCTOR, jwt);

            assertThat(response.role()).isEqualTo(Role.INSTRUCTOR);
            verify(userRepository).saveAndFlush(target);
            verify(keycloakAdminService).addRealmRole(OTHER, "INSTRUCTOR");
            verify(keycloakAdminService).removeRealmRole(OTHER, "ADMIN");
            verify(keycloakAdminService, never()).removeRealmRole(OTHER, "INSTRUCTOR");
        }

        @Test
        void demoteToStudentRemovesElevatedRolesAndNeverTouchesDefaultStudentRole() {
            target.setRole(Role.INSTRUCTOR);
            given(userRepository.findById(2L)).willReturn(Optional.of(target));

            userService.changeRole(2L, Role.STUDENT, jwt);

            verify(keycloakAdminService).removeRealmRole(OTHER, "ADMIN");
            verify(keycloakAdminService).removeRealmRole(OTHER, "INSTRUCTOR");
            verify(keycloakAdminService, never()).addRealmRole(any(), anyString());
            verify(keycloakAdminService, never()).removeRealmRole(OTHER, "STUDENT");
        }

        @Test
        void sameRoleIsANoOp() {
            given(userRepository.findById(2L)).willReturn(Optional.of(target));

            userService.changeRole(2L, Role.STUDENT, jwt);

            verify(userRepository, never()).saveAndFlush(any());
            verifyNoInteractions(keycloakAdminService);
        }

        @Test
        void adminCannotChangeOwnRole() {
            User admin = user(1L, ME, Role.ADMIN, "admin@elearning.local", "Platform Admin");
            given(userRepository.findById(1L)).willReturn(Optional.of(admin));

            assertThatThrownBy(() -> userService.changeRole(1L, Role.STUDENT, jwt))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(keycloakAdminService);
        }

        @Test
        void unknownUserIs404() {
            given(userRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.changeRole(99L, Role.ADMIN, jwt))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class AdminStatus {

        @Test
        void disableUpdatesLocalAndKeycloak() {
            User target = user(2L, OTHER, Role.STUDENT, "student2@elearning.local", "Rith Sok");
            given(userRepository.findById(2L)).willReturn(Optional.of(target));

            UserResponse response = userService.changeStatus(2L, false, jwt);

            assertThat(response.active()).isFalse();
            verify(keycloakAdminService).setEnabled(OTHER, false);
        }

        @Test
        void unchangedStatusIsANoOp() {
            User target = user(2L, OTHER, Role.STUDENT, "student2@elearning.local", "Rith Sok");
            given(userRepository.findById(2L)).willReturn(Optional.of(target));

            userService.changeStatus(2L, true, jwt);

            verify(keycloakAdminService, never()).setEnabled(any(), anyBoolean());
        }

        @Test
        void adminCannotDisableSelf() {
            User admin = user(1L, ME, Role.ADMIN, "admin@elearning.local", "Platform Admin");
            given(userRepository.findById(1L)).willReturn(Optional.of(admin));

            assertThatThrownBy(() -> userService.changeStatus(1L, false, jwt))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    private static User user(Long id, UUID keycloakId, Role role, String email, String fullName) {
        User user = User.builder()
                .keycloakId(keycloakId)
                .role(role)
                .email(email)
                .fullName(fullName)
                .build();
        user.setId(id);
        return user;
    }

    private static Jwt jwt(UUID subject) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(subject.toString())
                .claim("preferred_username", "student1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }
}
