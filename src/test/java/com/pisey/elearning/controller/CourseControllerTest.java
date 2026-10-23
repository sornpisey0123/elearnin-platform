package com.pisey.elearning.controller;

import com.pisey.elearning.config.SecurityConfig;
import com.pisey.elearning.dto.common.PageResponse;
import com.pisey.elearning.dto.content.CourseOutlineResponse;
import com.pisey.elearning.dto.course.CourseCreateRequest;
import com.pisey.elearning.dto.course.CourseDetailResponse;
import com.pisey.elearning.dto.course.CourseSearchCriteria;
import com.pisey.elearning.dto.course.CourseSearchCriteria.CourseSort;
import com.pisey.elearning.dto.course.InstructorCourseResponse;
import com.pisey.elearning.entity.enums.CourseLevel;
import com.pisey.elearning.entity.enums.CourseStatus;
import com.pisey.elearning.entity.enums.LessonType;
import com.pisey.elearning.exception.BadRequestException;
import com.pisey.elearning.exception.ConflictException;
import com.pisey.elearning.exception.ForbiddenException;
import com.pisey.elearning.exception.ResourceNotFoundException;
import com.pisey.elearning.service.ContentService;
import com.pisey.elearning.service.CourseService;
import org.junit.jupiter.api.Nested;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Public catalogue and instructor course endpoints. */
@WebMvcTest({CourseController.class, InstructorCourseController.class})
@Import(SecurityConfig.class)
class CourseControllerTest {

    private static final String VALID_BODY =
            "{\"title\":\"Java\",\"categoryId\":1,\"level\":\"BEGINNER\",\"price\":19.99}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private CourseService courseService;

    @MockitoBean
    private ContentService contentService;

    @Nested
    class Catalogue {

        @Test
        void searchPassesFiltersSortAndPaging() throws Exception {
            given(courseService.search(any(), any(), anyInt(), anyInt()))
                    .willReturn(new PageResponse<>(List.of(), 1, 5, 0, 0));

            mockMvc.perform(get("/api/courses")
                            .param("keyword", "java").param("categoryId", "1").param("level", "BEGINNER")
                            .param("minPrice", "5").param("maxPrice", "50")
                            .param("sort", "popular").param("page", "1").param("size", "5"))
                    .andExpect(status().isOk());

            verify(courseService).search(
                    new CourseSearchCriteria("java", 1L, CourseLevel.BEGINNER, new BigDecimal("5"), new BigDecimal("50")),
                    CourseSort.POPULAR, 1, 5);
        }

        @Test
        void searchDefaultsToNewestPage0Size12() throws Exception {
            given(courseService.search(any(), any(), anyInt(), anyInt()))
                    .willReturn(new PageResponse<>(List.of(), 0, 12, 0, 0));

            mockMvc.perform(get("/api/courses")).andExpect(status().isOk());

            verify(courseService).search(new CourseSearchCriteria(null, null, null, null, null), CourseSort.NEWEST, 0, 12);
        }

        @Test
        void unknownSortIs400() throws Exception {
            mockMvc.perform(get("/api/courses").param("sort", "cheapest"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Unknown sort: cheapest; use newest, rating or popular"));
            verifyNoInteractions(courseService);
        }

        @Test
        void negativePriceAndBadLevelAre400() throws Exception {
            mockMvc.perform(get("/api/courses").param("minPrice", "-1")).andExpect(status().isBadRequest());
            mockMvc.perform(get("/api/courses").param("level", "EXPERT")).andExpect(status().isBadRequest());
            verifyNoInteractions(courseService);
        }

        @Test
        void detailIsPublicAndPassesNoCallerWithoutToken() throws Exception {
            given(courseService.findBySlug(eq("java"), isNull())).willReturn(detail(CourseStatus.PUBLISHED));

            mockMvc.perform(get("/api/courses/java"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.instructor.fullName").value("Dara Chan"))
                    .andExpect(jsonPath("$.lessonCount").value(5));
        }

        @Test
        void detailPassesCallerWhenTokenPresent() throws Exception {
            given(courseService.findBySlug(eq("java"), any(Jwt.class))).willReturn(detail(CourseStatus.DRAFT));

            mockMvc.perform(get("/api/courses/java").with(role("INSTRUCTOR")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DRAFT"));
        }

        @Test
        void outlineIsPublic() throws Exception {
            given(contentService.getOutline(eq(10L), isNull())).willReturn(new CourseOutlineResponse(10L, "Java",
                    List.of(new CourseOutlineResponse.SectionOutline(1L, "Intro", 1, List.of(
                            new CourseOutlineResponse.LessonOutline(5L, "Welcome", LessonType.VIDEO, 300, true, false))))));

            mockMvc.perform(get("/api/courses/10/outline"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sections[0].lessons[0].title").value("Welcome"))
                    .andExpect(jsonPath("$.sections[0].lessons[0].hasQuiz").value(false))
                    .andExpect(jsonPath("$.sections[0].lessons[0].videoUrl").doesNotExist())
                    .andExpect(jsonPath("$.sections[0].lessons[0].body").doesNotExist())
                    .andExpect(jsonPath("$.sections[0].lessons[0].fileUrl").doesNotExist());
        }

        @Test
        void hiddenCourseIs404() throws Exception {
            given(courseService.findBySlug(eq("draft"), any())).willThrow(new ResourceNotFoundException("Course", "slug", "draft"));

            mockMvc.perform(get("/api/courses/draft")).andExpect(status().isNotFound());
        }
    }

    @Nested
    class Instructor {

        @Test
        void createIs201Draft() throws Exception {
            given(courseService.create(any(Jwt.class), eq(new CourseCreateRequest("Java", null, 1L,
                    CourseLevel.BEGINNER, new BigDecimal("19.99"))))).willReturn(detail(CourseStatus.DRAFT));

            mockMvc.perform(post("/api/instructor/courses").with(role("INSTRUCTOR"))
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("DRAFT"));
        }

        @Test
        void createValidatesFields() throws Exception {
            mockMvc.perform(post("/api/instructor/courses").with(role("INSTRUCTOR"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"\",\"price\":-1}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.title").exists())
                    .andExpect(jsonPath("$.errors.price").exists())
                    .andExpect(jsonPath("$.errors.categoryId").exists())
                    .andExpect(jsonPath("$.errors.level").exists());
            verifyNoInteractions(courseService);
        }

        @Test
        void studentGets403AndAnonymous401() throws Exception {
            mockMvc.perform(post("/api/instructor/courses").with(role("STUDENT"))
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/instructor/courses")
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(courseService);
        }

        @Test
        void listOwnWithStatusFilter() throws Exception {
            InstructorCourseResponse row = new InstructorCourseResponse(10L, "Java", "java", null, CourseStatus.DRAFT,
                    CourseLevel.BEGINNER, BigDecimal.TEN, BigDecimal.ZERO, 0, 0, "Programming", null, Instant.now());
            given(courseService.findOwn(any(Jwt.class), eq(CourseStatus.DRAFT), any()))
                    .willReturn(new PageResponse<>(List.of(row), 0, 12, 1, 1));

            mockMvc.perform(get("/api/instructor/courses").param("status", "DRAFT").with(role("INSTRUCTOR")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].status").value("DRAFT"));
        }

        @Test
        void updateNotOwnerIs403() throws Exception {
            given(courseService.update(eq(10L), any(), any(Jwt.class)))
                    .willThrow(new ForbiddenException("You can only manage your own courses"));

            mockMvc.perform(put("/api/instructor/courses/10").with(role("INSTRUCTOR"))
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.detail").value("You can only manage your own courses"));
        }

        @Test
        void updateUnknownIs404() throws Exception {
            given(courseService.update(eq(99L), any(), any(Jwt.class)))
                    .willThrow(new ResourceNotFoundException("Course", "id", 99L));

            mockMvc.perform(put("/api/instructor/courses/99").with(role("INSTRUCTOR"))
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isNotFound());
        }

        @Test
        void uploadThumbnail() throws Exception {
            given(courseService.updateThumbnail(eq(10L), any(), any(Jwt.class))).willReturn(detail(CourseStatus.DRAFT));

            mockMvc.perform(multipart("/api/instructor/courses/10/thumbnail")
                            .file(new MockMultipartFile("file", "t.png", "image/png", new byte[]{1}))
                            .with(role("INSTRUCTOR")))
                    .andExpect(status().isOk());
        }

        @Test
        void publishWithoutLessonsIs400() throws Exception {
            given(courseService.publish(eq(10L), any(Jwt.class)))
                    .willThrow(new BadRequestException("Cannot publish: add at least one section"));

            mockMvc.perform(post("/api/instructor/courses/10/publish").with(role("INSTRUCTOR")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Cannot publish: add at least one section"));
        }

        @Test
        void unpublishWithActiveEnrollmentsIs409() throws Exception {
            given(courseService.unpublish(eq(10L), any(Jwt.class)))
                    .willThrow(new ConflictException("Cannot unpublish: the course has active enrollments"));

            mockMvc.perform(post("/api/instructor/courses/10/unpublish").with(role("INSTRUCTOR")))
                    .andExpect(status().isConflict());
        }

        @Test
        void deleteWithEnrollmentsIs200Archived() throws Exception {
            given(courseService.delete(eq(10L), any(Jwt.class))).willReturn(true);

            mockMvc.perform(delete("/api/instructor/courses/10").with(role("INSTRUCTOR")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.archived").value(true));
        }

        @Test
        void deleteWithoutEnrollmentsIs204() throws Exception {
            given(courseService.delete(eq(10L), any(Jwt.class))).willReturn(false);

            mockMvc.perform(delete("/api/instructor/courses/10").with(role("INSTRUCTOR")))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));
        }

        @Test
        void adminMayUseInstructorEndpoints() throws Exception {
            given(courseService.delete(eq(10L), any(Jwt.class))).willReturn(false);

            mockMvc.perform(delete("/api/instructor/courses/10").with(role("ADMIN")))
                    .andExpect(status().isNoContent());
        }
    }

    private static CourseDetailResponse detail(CourseStatus status) {
        return new CourseDetailResponse(10L, "Java", "java", null, CourseLevel.BEGINNER, BigDecimal.TEN, null, status,
                BigDecimal.ZERO, 0, 0, null, Instant.now(), Instant.now(),
                new CourseDetailResponse.InstructorInfo(2L, "Dara Chan", null, null),
                new CourseDetailResponse.CategoryInfo(1L, "Programming", "programming"),
                2, 5, 3600);
    }

    private static RequestPostProcessor role(String role) {
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
