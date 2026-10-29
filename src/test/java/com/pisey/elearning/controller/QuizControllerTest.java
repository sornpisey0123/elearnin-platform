package com.pisey.elearning.controller;

import com.pisey.elearning.config.SecurityConfig;
import com.pisey.elearning.dto.quiz.AttemptResultResponse;
import com.pisey.elearning.dto.quiz.AttemptStartResponse;
import com.pisey.elearning.dto.quiz.AttemptSubmitRequest;
import com.pisey.elearning.dto.quiz.AttemptSummaryResponse;
import com.pisey.elearning.dto.quiz.QuizCreateRequest;
import com.pisey.elearning.dto.quiz.QuizResponse;
import com.pisey.elearning.dto.quiz.QuizSummaryResponse;
import com.pisey.elearning.dto.quiz.QuizUpdateRequest;
import com.pisey.elearning.dto.quiz.StudentQuizResponse;
import com.pisey.elearning.entity.enums.QuestionType;
import com.pisey.elearning.exception.BadRequestException;
import com.pisey.elearning.exception.ConflictException;
import com.pisey.elearning.exception.ForbiddenException;
import com.pisey.elearning.exception.ResourceNotFoundException;
import com.pisey.elearning.service.QuizService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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

@WebMvcTest({InstructorQuizController.class, QuizController.class})
@Import(SecurityConfig.class)
class QuizControllerTest {

    private static final String QUIZ_JSON = """
            {"title":"Basics","passMark":60,"timeLimitMinutes":10,"questions":[
              {"questionText":"2+2?","type":"SINGLE_CHOICE","options":[
                {"optionText":"4","correct":true},{"optionText":"5"}]}]}
            """;

    private static final QuizResponse QUIZ = new QuizResponse(50L, 10L, 30L, null, "Basics", 60, 10, List.of(
            new QuizResponse.QuestionResponse(61L, "2+2?", QuestionType.SINGLE_CHOICE, 1, 1, List.of(
                    new QuizResponse.OptionResponse(71L, "4", true),
                    new QuizResponse.OptionResponse(72L, "5", false)))));

    private static final StudentQuizResponse STUDENT_VIEW = new StudentQuizResponse(50L, 10L, 30L, null, "Basics",
            60, 10, 1, List.of(new StudentQuizResponse.QuestionView(61L, "2+2?", QuestionType.SINGLE_CHOICE, 1, 1,
            List.of(new StudentQuizResponse.OptionView(71L, "4"), new StudentQuizResponse.OptionView(72L, "5")))));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private QuizService quizService;

    // --- instructor ---

    @Test
    void createLessonQuizIs201WithAnswers() throws Exception {
        given(quizService.createForLesson(eq(30L), any(QuizCreateRequest.class), any(Jwt.class))).willReturn(QUIZ);

        mockMvc.perform(post("/api/instructor/lessons/30/quizzes").with(role("INSTRUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(QUIZ_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(50))
                .andExpect(jsonPath("$.questions[0].options[0].correct").value(true));
    }

    @Test
    void createSectionQuizIs201() throws Exception {
        given(quizService.createForSection(eq(20L), any(QuizCreateRequest.class), any(Jwt.class))).willReturn(QUIZ);

        mockMvc.perform(post("/api/instructor/sections/20/quizzes").with(role("INSTRUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(QUIZ_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    void createValidatesBody() throws Exception {
        mockMvc.perform(post("/api/instructor/lessons/30/quizzes").with(role("INSTRUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"passMark\":101,\"timeLimitMinutes\":0,\"questions\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.passMark").exists())
                .andExpect(jsonPath("$.errors.timeLimitMinutes").exists())
                .andExpect(jsonPath("$.errors.questions").exists());
        verifyNoInteractions(quizService);
    }

    @Test
    void createNeedsTwoOptionsPerQuestion() throws Exception {
        mockMvc.perform(post("/api/instructor/lessons/30/quizzes").with(role("INSTRUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Q","passMark":50,"questions":[
                                  {"questionText":"?","type":"SINGLE_CHOICE","options":[{"optionText":"A","correct":true}]}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['questions[0].options']").exists());
        verifyNoInteractions(quizService);
    }

    @Test
    void businessRuleViolationIs400() throws Exception {
        given(quizService.createForLesson(eq(30L), any(QuizCreateRequest.class), any(Jwt.class)))
                .willThrow(new BadRequestException("questions[0]: SINGLE_CHOICE needs exactly one correct option"));

        mockMvc.perform(post("/api/instructor/lessons/30/quizzes").with(role("INSTRUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(QUIZ_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("questions[0]: SINGLE_CHOICE needs exactly one correct option"));
    }

    @Test
    void studentGets403AndAnonymous401OnInstructorRoutes() throws Exception {
        mockMvc.perform(post("/api/instructor/lessons/30/quizzes").with(role("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON).content(QUIZ_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/instructor/quizzes/50"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(quizService);
    }

    @Test
    void notOwnerIs403() throws Exception {
        given(quizService.getForInstructor(eq(50L), any(Jwt.class)))
                .willThrow(new ForbiddenException("You can only manage your own courses"));

        mockMvc.perform(get("/api/instructor/quizzes/50").with(role("INSTRUCTOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateWithAttemptsIs409() throws Exception {
        given(quizService.update(eq(50L), any(QuizUpdateRequest.class), any(Jwt.class)))
                .willThrow(new ConflictException("Quiz already has attempts; create a new quiz instead of editing this one"));

        mockMvc.perform(put("/api/instructor/quizzes/50").with(role("INSTRUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(QUIZ_JSON))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteIs204() throws Exception {
        mockMvc.perform(delete("/api/instructor/quizzes/50").with(role("INSTRUCTOR")))
                .andExpect(status().isNoContent());
        verify(quizService).delete(eq(50L), any(Jwt.class));
    }

    @Test
    void unknownQuizIs404() throws Exception {
        willThrow(new ResourceNotFoundException("Quiz", "id", 99L)).given(quizService).delete(eq(99L), any(Jwt.class));

        mockMvc.perform(delete("/api/instructor/quizzes/99").with(role("INSTRUCTOR")))
                .andExpect(status().isNotFound());
    }

    // --- students ---

    @Test
    void studentViewNeverExposesCorrectFlag() throws Exception {
        given(quizService.getForStudent(eq(50L), any(Jwt.class))).willReturn(STUDENT_VIEW);

        mockMvc.perform(get("/api/quizzes/50").with(role("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].options[0].optionText").value("4"))
                .andExpect(jsonPath("$.questions[0].options[0].correct").doesNotExist())
                .andExpect(jsonPath("$.questions[0].options[1].correct").doesNotExist());
    }

    @Test
    void quizRequiresToken() throws Exception {
        mockMvc.perform(get("/api/quizzes/50")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/courses/10/quizzes")).andExpect(status().isUnauthorized());
        verifyNoInteractions(quizService);
    }

    @Test
    void notEnrolledIs403() throws Exception {
        given(quizService.getForStudent(eq(50L), any(Jwt.class)))
                .willThrow(new ForbiddenException("Enroll to take this quiz"));

        mockMvc.perform(get("/api/quizzes/50").with(role("STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Enroll to take this quiz"));
    }

    @Test
    void courseQuizList() throws Exception {
        given(quizService.findByCourse(eq(10L), any(Jwt.class)))
                .willReturn(List.of(new QuizSummaryResponse(50L, 30L, null, "Basics", 60, 10)));

        mockMvc.perform(get("/api/courses/10/quizzes").with(role("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lessonId").value(30));
    }

    @Test
    void newAttemptIs201AndResumedIs200() throws Exception {
        Instant now = Instant.now();
        given(quizService.startAttempt(eq(50L), any(Jwt.class)))
                .willReturn(new AttemptStartResponse(80L, 50L, now, now.plusSeconds(600), false))
                .willReturn(new AttemptStartResponse(80L, 50L, now, now.plusSeconds(600), true));

        mockMvc.perform(post("/api/quizzes/50/attempts").with(role("STUDENT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attemptId").value(80))
                .andExpect(jsonPath("$.expiresAt").exists());
        mockMvc.perform(post("/api/quizzes/50/attempts").with(role("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumed").value(true));
    }

    @Test
    void submitReturnsGradedResult() throws Exception {
        AttemptSubmitRequest request = new AttemptSubmitRequest(List.of(
                new AttemptSubmitRequest.AnswerRequest(61L, List.of(71L))));
        given(quizService.submit(eq(80L), eq(request), any(Jwt.class))).willReturn(new AttemptResultResponse(
                80L, 50L, new BigDecimal("100.00"), true, false, 1, 1, Instant.now(), Instant.now(), List.of(
                new AttemptResultResponse.QuestionResult(61L, true, 1, 1, List.of(71L), List.of(71L)))));

        mockMvc.perform(post("/api/attempts/80/submit").with(role("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[{\"questionId\":61,\"optionIds\":[71]}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(100.00))
                .andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.questions[0].correctOptionIds[0]").value(71));
    }

    @Test
    void submitValidatesBody() throws Exception {
        mockMvc.perform(post("/api/attempts/80/submit").with(role("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answers\":[{\"optionIds\":[71]}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['answers[0].questionId']").exists());
        mockMvc.perform(post("/api/attempts/80/submit").with(role("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.answers").exists());
        verifyNoInteractions(quizService);
    }

    @Test
    void secondSubmitIs409AndForeignAttemptIs403() throws Exception {
        given(quizService.submit(eq(80L), any(AttemptSubmitRequest.class), any(Jwt.class)))
                .willThrow(new ConflictException("This attempt was already submitted"));
        given(quizService.submit(eq(81L), any(AttemptSubmitRequest.class), any(Jwt.class)))
                .willThrow(new ForbiddenException("You can only submit your own attempts"));

        mockMvc.perform(post("/api/attempts/80/submit").with(role("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answers\":[]}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/attempts/81/submit").with(role("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answers\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void myAttemptsNewestFirst() throws Exception {
        given(quizService.findMyAttempts(eq(50L), any(Jwt.class))).willReturn(List.of(
                new AttemptSummaryResponse(81L, 50L, null, null, Instant.now(), null),
                new AttemptSummaryResponse(80L, 50L, new BigDecimal("40.00"), false, Instant.now(), Instant.now())));

        mockMvc.perform(get("/api/quizzes/50/attempts/me").with(role("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].attemptId").value(81))
                .andExpect(jsonPath("$[1].passed").value(false));
    }

    private static RequestPostProcessor role(String role) {
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
