package com.pisey.elearning.controller;

import com.pisey.elearning.config.SecurityConfig;
import com.pisey.elearning.dto.common.PageResponse;
import com.pisey.elearning.dto.user.UserResponse;
import com.pisey.elearning.entity.enums.Role;
import com.pisey.elearning.exception.ForbiddenException;
import com.pisey.elearning.exception.ResourceNotFoundException;
import com.pisey.elearning.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUserController.class)
@Import(SecurityConfig.class)
class AdminUserControllerTest {

    private static final UserResponse STUDENT2 = new UserResponse(5L, UUID.randomUUID(), "Rith Sok",
            "student2@elearning.local", Role.STUDENT, true, null, Instant.now(), Instant.now());

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private UserService userService;

    @Test
    void listUsersWithFiltersAndDefaultPaging() throws Exception {
        given(userService.findAll(eq(Role.STUDENT), eq("rith"), any(Pageable.class)))
                .willReturn(new PageResponse<>(List.of(STUDENT2), 0, 12, 1, 1));

        mockMvc.perform(get("/api/admin/users").param("role", "STUDENT").param("keyword", "rith").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("student2@elearning.local"))
                .andExpect(jsonPath("$.totalElements").value(1));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(userService).findAll(eq(Role.STUDENT), eq("rith"), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(12);
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void listUsersRejectsUnknownRole() throws Exception {
        mockMvc.perform(get("/api/admin/users").param("role", "TEACHER").with(admin()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(userService);
    }

    @Test
    void studentGets403() throws Exception {
        mockMvc.perform(get("/api/admin/users").with(role("STUDENT")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(userService);
    }

    @Test
    void instructorGets403() throws Exception {
        mockMvc.perform(patch("/api/admin/users/5/role").with(role("INSTRUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(userService);
    }

    @Test
    void noTokenGets401() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changeRole() throws Exception {
        UserResponse promoted = new UserResponse(5L, STUDENT2.keycloakId(), "Rith Sok",
                "student2@elearning.local", Role.INSTRUCTOR, true, null, Instant.now(), Instant.now());
        given(userService.changeRole(eq(5L), eq(Role.INSTRUCTOR), any(Jwt.class))).willReturn(promoted);

        mockMvc.perform(patch("/api/admin/users/5/role").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"INSTRUCTOR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("INSTRUCTOR"));
    }

    @Test
    void changeRoleRequiresRole() throws Exception {
        mockMvc.perform(patch("/api/admin/users/5/role").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.role").exists());
    }

    @Test
    void changeRoleRejectsUnknownRoleValue() throws Exception {
        mockMvc.perform(patch("/api/admin/users/5/role").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"TEACHER\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(userService);
    }

    @Test
    void changeOwnRoleIs403() throws Exception {
        given(userService.changeRole(eq(1L), any(), any(Jwt.class)))
                .willThrow(new ForbiddenException("You cannot change your own role"));

        mockMvc.perform(patch("/api/admin/users/1/role").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("You cannot change your own role"));
    }

    @Test
    void changeRoleUnknownUserIs404() throws Exception {
        given(userService.changeRole(eq(99L), any(), any(Jwt.class)))
                .willThrow(new ResourceNotFoundException("User", "id", 99L));

        mockMvc.perform(patch("/api/admin/users/99/role").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void changeStatus() throws Exception {
        UserResponse disabled = new UserResponse(5L, STUDENT2.keycloakId(), "Rith Sok",
                "student2@elearning.local", Role.STUDENT, false, null, Instant.now(), Instant.now());
        given(userService.changeStatus(eq(5L), eq(false), any(Jwt.class))).willReturn(disabled);

        mockMvc.perform(patch("/api/admin/users/5/status").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void changeStatusRequiresActive() throws Exception {
        mockMvc.perform(patch("/api/admin/users/5/status").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.active").exists());
    }

    private static RequestPostProcessor admin() {
        return role("ADMIN");
    }

    private static RequestPostProcessor role(String role) {
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
