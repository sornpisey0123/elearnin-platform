package com.pisey.elearning.controller;

import com.pisey.elearning.config.SecurityConfig;
import com.pisey.elearning.dto.content.LessonCreateRequest;
import com.pisey.elearning.dto.content.LessonResourceResponse;
import com.pisey.elearning.dto.content.LessonResponse;
import com.pisey.elearning.dto.content.SectionCreateRequest;
import com.pisey.elearning.dto.content.SectionResponse;
import com.pisey.elearning.entity.enums.LessonType;
import com.pisey.elearning.exception.BadRequestException;
import com.pisey.elearning.exception.ForbiddenException;
import com.pisey.elearning.exception.ResourceNotFoundException;
import com.pisey.elearning.service.ContentService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({InstructorContentController.class, LessonController.class})
@Import(SecurityConfig.class)
class ContentControllerTest {

    private static final LessonResponse VIDEO = new LessonResponse(30L, 10L, 20L, "Welcome", LessonType.VIDEO,
            "https://v/1", 300, null, null, false, 1, List.of());

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ContentService contentService;

    @Test
    void createSectionIs201() throws Exception {
        given(contentService.createSection(eq(10L), eq(new SectionCreateRequest("Intro")), any(Jwt.class)))
                .willReturn(new SectionResponse(20L, 10L, "Intro", 1));

        mockMvc.perform(post("/api/instructor/courses/10/sections").with(role("INSTRUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Intro\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sortOrder").value(1));
    }

    @Test
    void createSectionValidatesTitle() throws Exception {
        mockMvc.perform(post("/api/instructor/courses/10/sections").with(role("INSTRUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists());
        verifyNoInteractions(contentService);
    }

    @Test
    void studentGets403AndAnonymous401OnInstructorContent() throws Exception {
        mockMvc.perform(post("/api/instructor/courses/10/sections").with(role("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Intro\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/instructor/lessons/30"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(contentService);
    }

    @Test
    void notOwnerIs403() throws Exception {
        willThrow(new ForbiddenException("You can only manage your own courses"))
                .given(contentService).deleteSection(eq(20L), any(Jwt.class));

        mockMvc.perform(delete("/api/instructor/sections/20").with(role("INSTRUCTOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteSectionIs204() throws Exception {
        mockMvc.perform(delete("/api/instructor/sections/20").with(role("INSTRUCTOR")))
                .andExpect(status().isNoContent());
        verify(contentService).deleteSection(eq(20L), any(Jwt.class));
    }

    @Test
    void reorderWithWrongIdSetIs400() throws Exception {
        given(contentService.reorderSections(eq(10L), eq(List.of(1L, 2L)), any(Jwt.class)))
                .willThrow(new BadRequestException("sectionIds must list every section id exactly once; expected [1, 2, 3]"));

        mockMvc.perform(put("/api/instructor/courses/10/sections/order").with(role("INSTRUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"sectionIds\":[1,2]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("sectionIds must list every section id exactly once; expected [1, 2, 3]"));
    }

    @Test
    void reorderWithEmptyOrNullIdsIs400() throws Exception {
        mockMvc.perform(put("/api/instructor/sections/20/lessons/order").with(role("INSTRUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lessonIds\":[]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/instructor/sections/20/lessons/order").with(role("INSTRUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lessonIds\":[1,null]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(contentService);
    }

    @Test
    void createLessonIs201AndValidatesTypeAndUrl() throws Exception {
        given(contentService.createLesson(eq(20L), eq(new LessonCreateRequest("Welcome", LessonType.VIDEO,
                "https://v/1", 300, null, null)), any(Jwt.class))).willReturn(VIDEO);

        mockMvc.perform(post("/api/instructor/sections/20/lessons").with(role("INSTRUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Welcome\",\"type\":\"VIDEO\",\"videoUrl\":\"https://v/1\",\"durationSeconds\":300}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.videoUrl").value("https://v/1"));

        mockMvc.perform(post("/api/instructor/sections/20/lessons").with(role("INSTRUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"X\",\"videoUrl\":\"ftp://nope\",\"durationSeconds\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.type").exists())
                .andExpect(jsonPath("$.errors.videoUrl").value("must be an http(s) URL"))
                .andExpect(jsonPath("$.errors.durationSeconds").exists());
    }

    @Test
    void addResourceNeedsTitleAndFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "s.pdf", "application/pdf", new byte[]{1});
        given(contentService.addResource(eq(30L), eq("Slides"), any(), any(Jwt.class)))
                .willReturn(new LessonResourceResponse(40L, "Slides", "/files/resources/a.pdf", "application/pdf", 1L));

        mockMvc.perform(multipart("/api/instructor/lessons/30/resources").file(file).param("title", "Slides")
                        .with(role("INSTRUCTOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileUrl").value("/files/resources/a.pdf"));

        mockMvc.perform(multipart("/api/instructor/lessons/30/resources").file(file).param("title", " ")
                        .with(role("INSTRUCTOR")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/instructor/lessons/30/resources").file(file).with(role("INSTRUCTOR")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lessonContentForbiddenWithEnrollMessage() throws Exception {
        given(contentService.getLesson(eq(30L), any(Jwt.class))).willThrow(new ForbiddenException("Enroll to access this lesson"));

        mockMvc.perform(get("/api/lessons/30").with(role("STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Enroll to access this lesson"));
    }

    @Test
    void lessonContentRequiresToken() throws Exception {
        mockMvc.perform(get("/api/lessons/30")).andExpect(status().isUnauthorized());
        verifyNoInteractions(contentService);
    }

    @Test
    void lessonContentOkAnd404() throws Exception {
        given(contentService.getLesson(eq(30L), any(Jwt.class))).willReturn(VIDEO);
        given(contentService.getLesson(eq(99L), any(Jwt.class))).willThrow(new ResourceNotFoundException("Lesson", "id", 99L));

        mockMvc.perform(get("/api/lessons/30").with(role("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videoUrl").value("https://v/1"));
        mockMvc.perform(get("/api/lessons/99").with(role("STUDENT")))
                .andExpect(status().isNotFound());
    }

    private static RequestPostProcessor role(String role) {
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
