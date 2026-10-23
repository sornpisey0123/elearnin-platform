package com.pisey.elearning.controller;

import com.pisey.elearning.config.SecurityConfig;
import com.pisey.elearning.dto.category.CategoryCreateRequest;
import com.pisey.elearning.dto.category.CategoryResponse;
import com.pisey.elearning.dto.category.CategoryUpdateRequest;
import com.pisey.elearning.dto.common.PageResponse;
import com.pisey.elearning.dto.course.CourseSummaryResponse;
import com.pisey.elearning.entity.enums.CourseLevel;
import com.pisey.elearning.exception.ConflictException;
import com.pisey.elearning.exception.ResourceNotFoundException;
import com.pisey.elearning.service.CategoryService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Public and admin category endpoints together, since they share the service mock. */
@WebMvcTest({CategoryController.class, AdminCategoryController.class})
@Import(SecurityConfig.class)
class CategoryControllerTest {

    private static final CategoryResponse PROGRAMMING = new CategoryResponse(1L, "Programming", "programming", null, 2);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private CategoryService categoryService;

    @Nested
    class Public {

        @Test
        void listNeedsNoToken() throws Exception {
            given(categoryService.findAll()).willReturn(List.of(PROGRAMMING));

            mockMvc.perform(get("/api/categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].slug").value("programming"))
                    .andExpect(jsonPath("$[0].courseCount").value(2));
        }

        @Test
        void coursesByCategoryArePagedNewestFirstByDefault() throws Exception {
            CourseSummaryResponse course = new CourseSummaryResponse(10L, "Java Basics", "java-basics", null,
                    CourseLevel.BEGINNER, new BigDecimal("19.99"), BigDecimal.ZERO, 0, 3, "Dara Chan", "Programming");
            given(categoryService.findPublishedCourses(eq("programming"), any()))
                    .willReturn(new PageResponse<>(List.of(course), 0, 12, 1, 1));

            mockMvc.perform(get("/api/categories/programming/courses"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].instructorName").value("Dara Chan"))
                    .andExpect(jsonPath("$.content[0].price").value(19.99))
                    .andExpect(jsonPath("$.size").value(12));

            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(categoryService).findPublishedCourses(eq("programming"), pageable.capture());
            assertThat(pageable.getValue().getPageSize()).isEqualTo(12);
            assertThat(pageable.getValue().getSort().getOrderFor("publishedAt").getDirection())
                    .isEqualTo(Sort.Direction.DESC);
        }

        @Test
        void pageSizeIsCappedAt50() throws Exception {
            given(categoryService.findPublishedCourses(eq("programming"), any()))
                    .willReturn(new PageResponse<>(List.of(), 0, 50, 0, 0));

            mockMvc.perform(get("/api/categories/programming/courses").param("size", "500"))
                    .andExpect(status().isOk());

            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(categoryService).findPublishedCourses(eq("programming"), pageable.capture());
            assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        }

        @Test
        void unknownSlugIs404() throws Exception {
            given(categoryService.findPublishedCourses(eq("nope"), any()))
                    .willThrow(new ResourceNotFoundException("Category", "slug", "nope"));

            mockMvc.perform(get("/api/categories/nope/courses"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Resource not found"));
        }
    }

    @Nested
    class Admin {

        @Test
        void createIs201() throws Exception {
            given(categoryService.create(new CategoryCreateRequest("Data Science", "ML")))
                    .willReturn(new CategoryResponse(4L, "Data Science", "data-science", "ML", 0));

            mockMvc.perform(post("/api/admin/categories").with(role("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Data Science\",\"description\":\"ML\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.slug").value("data-science"));
        }

        @Test
        void createValidatesName() throws Exception {
            mockMvc.perform(post("/api/admin/categories").with(role("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name").exists());
            verifyNoInteractions(categoryService);
        }

        @Test
        void createDuplicateIs409() throws Exception {
            given(categoryService.create(any())).willThrow(new ConflictException("Category name already exists: Design"));

            mockMvc.perform(post("/api/admin/categories").with(role("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Design\"}"))
                    .andExpect(status().isConflict());
        }

        @Test
        void createWithoutTokenIs401() throws Exception {
            mockMvc.perform(post("/api/admin/categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"X\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void instructorAndStudentGet403() throws Exception {
            for (String role : List.of("INSTRUCTOR", "STUDENT")) {
                mockMvc.perform(post("/api/admin/categories").with(role(role))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"X\"}"))
                        .andExpect(status().isForbidden());
            }
            verifyNoInteractions(categoryService);
        }

        @Test
        void updateIs200() throws Exception {
            given(categoryService.update(1L, new CategoryUpdateRequest("Programming", "All languages")))
                    .willReturn(new CategoryResponse(1L, "Programming", "programming", "All languages", 2));

            mockMvc.perform(put("/api/admin/categories/1").with(role("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Programming\",\"description\":\"All languages\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.courseCount").value(2));
        }

        @Test
        void updateUnknownIs404() throws Exception {
            given(categoryService.update(eq(9L), any())).willThrow(new ResourceNotFoundException("Category", "id", 9L));

            mockMvc.perform(put("/api/admin/categories/9").with(role("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"X\"}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void deleteIs204() throws Exception {
            mockMvc.perform(delete("/api/admin/categories/3").with(role("ADMIN")))
                    .andExpect(status().isNoContent());
            verify(categoryService).delete(3L);
        }

        @Test
        void deleteInUseIs409() throws Exception {
            willThrow(new ConflictException("Category is still used by one or more courses"))
                    .given(categoryService).delete(1L);

            mockMvc.perform(delete("/api/admin/categories/1").with(role("ADMIN")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("Category is still used by one or more courses"));
        }
    }

    private static RequestPostProcessor role(String role) {
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
