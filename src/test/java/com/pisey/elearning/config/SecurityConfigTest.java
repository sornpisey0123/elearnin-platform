package com.pisey.elearning.config;

import com.pisey.elearning.controller.DebugTokenController;
import com.pisey.elearning.entity.User;
import com.pisey.elearning.entity.enums.Role;
import com.pisey.elearning.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DebugTokenController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    private static final String SUB = UUID.randomUUID().toString();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SecurityConfig securityConfig;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private CurrentUserService currentUserService;

    @Test
    void noTokenReturns401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/me/debug-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void debugTokenReturnsClaimsAuthoritiesAndLocalUser() throws Exception {
        User user = User.builder()
                .keycloakId(UUID.fromString(SUB))
                .role(Role.STUDENT)
                .email("student1@elearning.local")
                .fullName("Vanna Kim")
                .build();
        user.setId(7L);
        given(currentUserService.getOrCreate(any(Jwt.class))).willReturn(user);

        mockMvc.perform(get("/api/me/debug-token")
                        .with(jwt().jwt(j -> j.subject(SUB).claim("email", "student1@elearning.local"))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorities[0]").value("ROLE_STUDENT"))
                .andExpect(jsonPath("$.localUser.id").value(7))
                .andExpect(jsonPath("$.localUser.role").value("STUDENT"))
                .andExpect(jsonPath("$.claims.sub").value(SUB));
    }

    @Test
    void adminPathRejectsStudentWith403ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .with(jwt().jwt(j -> j.subject(SUB)).authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    void adminPathLetsAdminThrough() throws Exception {
        // No admin controller exists yet, so getting past security means 404, not 403
        mockMvc.perform(get("/api/admin/users")
                        .with(jwt().jwt(j -> j.subject(SUB)).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void instructorPathRejectsStudent() throws Exception {
        mockMvc.perform(get("/api/instructor/courses")
                        .with(jwt().jwt(j -> j.subject(SUB)).authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void instructorPathLetsInstructorThrough() throws Exception {
        mockMvc.perform(get("/api/instructor/courses")
                        .with(jwt().jwt(j -> j.subject(SUB)).authorities(new SimpleGrantedAuthority("ROLE_INSTRUCTOR"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void instructorPathLetsAdminThroughForOwnershipBypass() throws Exception {
        mockMvc.perform(get("/api/instructor/courses")
                        .with(jwt().jwt(j -> j.subject(SUB)).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicCatalogueNeedsNoToken() throws Exception {
        mockMvc.perform(get("/api/courses"))
                .andExpect(status().isNotFound());
    }

    @Test
    void lessonContentIsNotPublic() throws Exception {
        mockMvc.perform(get("/api/lessons/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void converterMapsKeycloakRealmRolesToAuthorities() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(SUB)
                .claim("realm_access", Map.of("roles", List.of("INSTRUCTOR", "STUDENT", "offline_access")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        Collection<GrantedAuthority> authorities = securityConfig.keycloakJwtConverter().convert(jwt).getAuthorities();

        // Security 7 also adds a FACTOR_BEARER authority; only the role mapping is ours
        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .filteredOn(authority -> authority.startsWith("ROLE_"))
                .containsExactlyInAnyOrder("ROLE_INSTRUCTOR", "ROLE_STUDENT", "ROLE_offline_access");
    }

    @Test
    void converterHandlesTokenWithoutRealmAccess() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(SUB)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        assertThat(securityConfig.keycloakJwtConverter().convert(jwt).getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .noneMatch(authority -> authority.startsWith("ROLE_"));
    }
}
