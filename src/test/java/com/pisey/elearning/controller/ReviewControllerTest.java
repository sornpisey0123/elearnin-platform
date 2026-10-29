package com.pisey.elearning.controller;

import com.pisey.elearning.config.SecurityConfig;
import com.pisey.elearning.dto.common.PageResponse;
import com.pisey.elearning.dto.review.ReviewCreateRequest;
import com.pisey.elearning.dto.review.ReviewResponse;
import com.pisey.elearning.dto.review.ReviewUpdateRequest;
import com.pisey.elearning.exception.ConflictException;
import com.pisey.elearning.exception.ForbiddenException;
import com.pisey.elearning.exception.ResourceNotFoundException;
import com.pisey.elearning.service.ReviewService;
import org.junit.jupiter.api.Test;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

@WebMvcTest(ReviewController.class)
@Import(SecurityConfig.class)
class ReviewControllerTest {

    private static final ReviewResponse REVIEW = new ReviewResponse(90L, 10L, 5, "Great", Instant.now(), null,
            new ReviewResponse.Author(2L, "Vanna", "/files/avatars/v.png"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ReviewService reviewService;

    @Test
    void listIsPublicAndNewestFirstByDefault() throws Exception {
        given(reviewService.findByCourse(eq(10L), any(Pageable.class), isNull()))
                .willReturn(new PageResponse<>(List.of(REVIEW), 0, 12, 1, 1));

        mockMvc.perform(get("/api/courses/10/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].author.fullName").value("Vanna"))
                .andExpect(jsonPath("$.content[0].author.avatarUrl").value("/files/avatars/v.png"));
        verify(reviewService).findByCourse(eq(10L),
                argThat(pageable -> Sort.Direction.DESC.equals(pageable.getSort().getOrderFor("createdAt").getDirection())),
                isNull());
    }

    @Test
    void createIs201() throws Exception {
        given(reviewService.create(eq(10L), eq(new ReviewCreateRequest(5, "Great")), any(Jwt.class))).willReturn(REVIEW);

        mockMvc.perform(post("/api/courses/10/reviews").with(role("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5,\"comment\":\"Great\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(90));
    }

    @Test
    void createValidatesRatingAndCommentLength() throws Exception {
        mockMvc.perform(post("/api/courses/10/reviews").with(role("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":6,\"comment\":\"" + "x".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.rating").exists())
                .andExpect(jsonPath("$.errors.comment").exists());
        mockMvc.perform(post("/api/courses/10/reviews").with(role("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.rating").exists());
        verifyNoInteractions(reviewService);
    }

    @Test
    void createNeedsStudentRoleAndToken() throws Exception {
        mockMvc.perform(post("/api/courses/10/reviews").with(role("INSTRUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/courses/10/reviews")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(reviewService);
    }

    @Test
    void notEnrolledIs403AndSecondReviewIs409() throws Exception {
        given(reviewService.create(eq(10L), any(ReviewCreateRequest.class), any(Jwt.class)))
                .willThrow(new ForbiddenException("Only enrolled students can review this course"));
        given(reviewService.create(eq(11L), any(ReviewCreateRequest.class), any(Jwt.class)))
                .willThrow(new ConflictException("You already reviewed this course"));

        mockMvc.perform(post("/api/courses/10/reviews").with(role("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/courses/11/reviews").with(role("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5}"))
                .andExpect(status().isConflict());
    }

    @Test
    void updateByAuthorAnd403ForOthers() throws Exception {
        given(reviewService.update(eq(90L), eq(new ReviewUpdateRequest(4, null)), any(Jwt.class))).willReturn(REVIEW);
        given(reviewService.update(eq(91L), any(ReviewUpdateRequest.class), any(Jwt.class)))
                .willThrow(new ForbiddenException("You can only edit your own review"));

        mockMvc.perform(put("/api/reviews/90").with(role("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":4}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/reviews/91").with(role("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":4}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteIs204And404ForUnknown() throws Exception {
        willThrow(new ResourceNotFoundException("Review", "id", 99L))
                .given(reviewService).delete(eq(99L), any(Jwt.class));

        mockMvc.perform(delete("/api/reviews/90").with(role("ADMIN")))
                .andExpect(status().isNoContent());
        verify(reviewService).delete(eq(90L), any(Jwt.class));
        mockMvc.perform(delete("/api/reviews/99").with(role("STUDENT")))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/reviews/90"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void myReviewOr404() throws Exception {
        given(reviewService.findMine(eq(10L), any(Jwt.class))).willReturn(REVIEW);
        given(reviewService.findMine(eq(11L), any(Jwt.class)))
                .willThrow(new ResourceNotFoundException("You have not reviewed course 11"));

        mockMvc.perform(get("/api/me/courses/10/review").with(role("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(5));
        mockMvc.perform(get("/api/me/courses/11/review").with(role("STUDENT")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/me/courses/10/review"))
                .andExpect(status().isUnauthorized());
    }

    private static RequestPostProcessor role(String role) {
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
