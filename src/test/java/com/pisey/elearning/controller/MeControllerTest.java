package com.pisey.elearning.controller;

import com.pisey.elearning.config.SecurityConfig;
import com.pisey.elearning.dto.user.PasswordChangeRequest;
import com.pisey.elearning.dto.user.ProfileResponse;
import com.pisey.elearning.dto.user.ProfileUpdateRequest;
import com.pisey.elearning.entity.enums.Role;
import com.pisey.elearning.exception.BadRequestException;
import com.pisey.elearning.exception.ConflictException;
import com.pisey.elearning.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MeController.class)
@Import(SecurityConfig.class)
class MeControllerTest {

    private static final ProfileResponse PROFILE =
            new ProfileResponse(3L, "Vanna Kim", "student1@elearning.local", Role.STUDENT, null, null);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private UserService userService;

    @Test
    void getProfile() throws Exception {
        given(userService.getProfile(any(Jwt.class))).willReturn(PROFILE);

        mockMvc.perform(get("/api/me").with(student()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    void getProfileWithoutTokenIs401() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(userService);
    }

    @Test
    void updateProfile() throws Exception {
        given(userService.updateProfile(any(Jwt.class), eq(new ProfileUpdateRequest("Vanna Kim", "bio", null))))
                .willReturn(PROFILE);

        mockMvc.perform(put("/api/me").with(student())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Vanna Kim\",\"bio\":\"bio\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Vanna Kim"));
    }

    @Test
    void updateProfileValidatesNameAndEmail() throws Exception {
        mockMvc.perform(put("/api/me").with(student())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\" \",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.fullName").exists())
                .andExpect(jsonPath("$.errors.email").exists());
        verifyNoInteractions(userService);
    }

    @Test
    void updateProfileEmailTakenIs409() throws Exception {
        given(userService.updateProfile(any(Jwt.class), any())).willThrow(new ConflictException("Email already in use"));

        mockMvc.perform(put("/api/me").with(student())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Vanna Kim\",\"email\":\"taken@elearning.local\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void uploadAvatar() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "me.png", "image/png", new byte[]{1, 2});
        given(userService.updateAvatar(any(Jwt.class), any())).willReturn(
                new ProfileResponse(3L, "Vanna Kim", "student1@elearning.local", Role.STUDENT, "/files/avatars/a.png", null));

        mockMvc.perform(multipart("/api/me/avatar").file(file).with(student()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value("/files/avatars/a.png"));
    }

    @Test
    void uploadAvatarWithoutFileIs400() throws Exception {
        mockMvc.perform(multipart("/api/me/avatar").with(student()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadAvatarWrongTypeIs400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "me.gif", "image/gif", new byte[]{1});
        given(userService.updateAvatar(any(Jwt.class), any())).willThrow(new BadRequestException("Unsupported file type"));

        mockMvc.perform(multipart("/api/me/avatar").file(file).with(student()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unsupported file type"));
    }

    @Test
    void changePasswordIs204() throws Exception {
        mockMvc.perform(put("/api/me/password").with(student())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Student@123\",\"newPassword\":\"NewPass@123\",\"confirmPassword\":\"NewPass@123\"}"))
                .andExpect(status().isNoContent());
        verify(userService).changePassword(any(Jwt.class),
                eq(new PasswordChangeRequest("Student@123", "NewPass@123", "NewPass@123")));
    }

    @Test
    void changePasswordTooShortIs400() throws Exception {
        mockMvc.perform(put("/api/me/password").with(student())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Student@123\",\"newPassword\":\"short\",\"confirmPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").exists());
        verifyNoInteractions(userService);
    }

    @Test
    void changePasswordWrongCurrentIs400() throws Exception {
        willThrow(new BadRequestException("Current password is incorrect"))
                .given(userService).changePassword(any(Jwt.class), any());

        mockMvc.perform(put("/api/me/password").with(student())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"NewPass@123\",\"confirmPassword\":\"NewPass@123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Current password is incorrect"));
    }

    private static RequestPostProcessor student() {
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"));
    }
}
