package com.pisey.elearning.service.impl;

import com.pisey.elearning.dto.quiz.AttemptResultResponse;
import com.pisey.elearning.dto.quiz.AttemptStartResponse;
import com.pisey.elearning.dto.quiz.AttemptSubmitRequest;
import com.pisey.elearning.dto.quiz.AttemptSubmitRequest.AnswerRequest;
import com.pisey.elearning.dto.quiz.QuizCreateRequest;
import com.pisey.elearning.dto.quiz.QuizOptionRequest;
import com.pisey.elearning.dto.quiz.QuizQuestionRequest;
import com.pisey.elearning.dto.quiz.QuizResponse;
import com.pisey.elearning.dto.quiz.QuizUpdateRequest;
import com.pisey.elearning.dto.quiz.StudentQuizResponse;
import com.pisey.elearning.entity.Category;
import com.pisey.elearning.entity.Course;
import com.pisey.elearning.entity.Lesson;
import com.pisey.elearning.entity.Quiz;
import com.pisey.elearning.entity.QuizAttempt;
import com.pisey.elearning.entity.QuizOption;
import com.pisey.elearning.entity.QuizQuestion;
import com.pisey.elearning.entity.Section;
import com.pisey.elearning.entity.User;
import com.pisey.elearning.entity.enums.CourseLevel;
import com.pisey.elearning.entity.enums.CourseStatus;
import com.pisey.elearning.entity.enums.LessonType;
import com.pisey.elearning.entity.enums.QuestionType;
import com.pisey.elearning.entity.enums.Role;
import com.pisey.elearning.exception.BadRequestException;
import com.pisey.elearning.exception.ConflictException;
import com.pisey.elearning.exception.ForbiddenException;
import com.pisey.elearning.exception.ResourceNotFoundException;
import com.pisey.elearning.mapper.QuizMapperImpl;
import com.pisey.elearning.repository.CourseRepository;
import com.pisey.elearning.repository.EnrollmentRepository;
import com.pisey.elearning.repository.LessonRepository;
import com.pisey.elearning.repository.QuizAttemptRepository;
import com.pisey.elearning.repository.QuizRepository;
import com.pisey.elearning.repository.SectionRepository;
import com.pisey.elearning.service.CurrentUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuizServiceImplTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID STUDENT = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    @Mock
    private QuizRepository quizRepository;
    @Mock
    private QuizAttemptRepository attemptRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private SectionRepository sectionRepository;
    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private CurrentUserService currentUserService;

    private QuizServiceImpl quizService;
    private Course course;
    private Section section;
    private Lesson lesson;
    private User student;

    @BeforeEach
    void setUp() {
        quizService = new QuizServiceImpl(quizRepository, attemptRepository, courseRepository, sectionRepository,
                lessonRepository, new CourseAccessServiceImpl(enrollmentRepository), currentUserService,
                new QuizMapperImpl());

        User owner = User.builder().keycloakId(OWNER).role(Role.INSTRUCTOR).fullName("Dara").email("d@x").build();
        student = User.builder().keycloakId(STUDENT).role(Role.STUDENT).fullName("Vanna").email("v@x").build();
        student.setId(2L);
        course = Course.builder().instructor(owner).category(Category.builder().name("P").slug("p").build())
                .title("Java").slug("java").level(CourseLevel.BEGINNER).price(BigDecimal.TEN)
                .status(CourseStatus.PUBLISHED).build();
        course.setId(10L);
        section = Section.builder().course(course).title("Intro").sortOrder(1).build();
        section.setId(20L);
        lesson = Lesson.builder().section(section).title("Welcome").type(LessonType.TEXT).body("x").sortOrder(1).build();
        lesson.setId(30L);

        given(lessonRepository.findWithCourseById(30L)).willReturn(Optional.of(lesson));
        given(sectionRepository.findWithCourseById(20L)).willReturn(Optional.of(section));
        given(courseRepository.findWithInstructorAndCategoryById(10L)).willReturn(Optional.of(course));
        given(quizRepository.save(any(Quiz.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(quizRepository.saveAndFlush(any(Quiz.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(currentUserService.getOrCreate(any(Jwt.class))).willReturn(student);
        actAs(OWNER, "INSTRUCTOR");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    class Create {

        @Test
        void keepsQuestionOrderAndDefaultsPointsToOne() {
            QuizResponse response = quizService.createForLesson(30L, request(
                    question(QuestionType.SINGLE_CHOICE, null, option("A", true), option("B", false)),
                    question(QuestionType.MULTIPLE_CHOICE, 3, option("A", true), option("B", true))), jwt(OWNER));

            assertThat(response.lessonId()).isEqualTo(30L);
            assertThat(response.sectionId()).isNull();
            assertThat(response.courseId()).isEqualTo(10L);
            assertThat(response.questions()).extracting(QuizResponse.QuestionResponse::sortOrder).containsExactly(1, 2);
            assertThat(response.questions()).extracting(QuizResponse.QuestionResponse::points).containsExactly(1, 3);
            assertThat(response.questions().getFirst().options())
                    .extracting(QuizResponse.OptionResponse::correct).containsExactly(true, false);
        }

        @Test
        void sectionQuizBelongsToTheSection() {
            QuizResponse response = quizService.createForSection(20L, request(
                    question(QuestionType.TRUE_FALSE, 1, option("True", true), option("False", false))), jwt(OWNER));

            assertThat(response.sectionId()).isEqualTo(20L);
            assertThat(response.lessonId()).isNull();
        }

        @Test
        void singleChoiceWithTwoCorrectOptionsIs400() {
            assertThatThrownBy(() -> quizService.createForLesson(30L, request(
                    question(QuestionType.SINGLE_CHOICE, 1, option("A", true), option("B", true))), jwt(OWNER)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("questions[0]: SINGLE_CHOICE needs exactly one correct option");
        }

        @Test
        void trueFalseNeedsExactlyTwoOptions() {
            assertThatThrownBy(() -> quizService.createForLesson(30L, request(
                    question(QuestionType.TRUE_FALSE, 1, option("T", true), option("F", false), option("?", false))),
                    jwt(OWNER)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("TRUE_FALSE");
        }

        @Test
        void multipleChoiceWithoutCorrectOptionIs400() {
            assertThatThrownBy(() -> quizService.createForLesson(30L, request(
                    question(QuestionType.SINGLE_CHOICE, 1, option("A", true), option("B", false)),
                    question(QuestionType.MULTIPLE_CHOICE, 1, option("A", false), option("B", false))), jwt(OWNER)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageStartingWith("questions[1]:");
        }

        @Test
        void quizWorthNoPointsIs400() {
            assertThatThrownBy(() -> quizService.createForLesson(30L, request(
                    question(QuestionType.SINGLE_CHOICE, 0, option("A", true), option("B", false))), jwt(OWNER)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("points");
        }

        @Test
        void notOwnerIs403() {
            actAs(OTHER, "INSTRUCTOR");

            assertThatThrownBy(() -> quizService.createForLesson(30L, validRequest(), jwt(OTHER)))
                    .isInstanceOf(ForbiddenException.class);
            verify(quizRepository, never()).save(any());
        }

        @Test
        void adminBypassesOwnership() {
            actAs(OTHER, "ADMIN");

            assertThat(quizService.createForLesson(30L, validRequest(), jwt(OTHER)).lessonId()).isEqualTo(30L);
        }

        @Test
        void unknownLessonIs404() {
            given(lessonRepository.findWithCourseById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> quizService.createForLesson(99L, validRequest(), jwt(OWNER)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class Update {

        @Test
        void quizWithAttemptsIs409() {
            Quiz quiz = quiz(null);
            given(attemptRepository.existsByQuizId(50L)).willReturn(true);

            assertThatThrownBy(() -> quizService.update(50L, updateRequest(), jwt(OWNER)))
                    .isInstanceOf(ConflictException.class);
            assertThat(quiz.getQuestions()).hasSize(2);
        }

        @Test
        void replacesSettingsAndQuestions() {
            quiz(null);

            QuizResponse response = quizService.update(50L, updateRequest(), jwt(OWNER));

            assertThat(response.title()).isEqualTo("Renamed");
            assertThat(response.timeLimitMinutes()).isEqualTo(5);
            assertThat(response.questions()).singleElement()
                    .satisfies(question -> assertThat(question.type()).isEqualTo(QuestionType.TRUE_FALSE));
        }

        @Test
        void notOwnerIs403() {
            quiz(null);
            actAs(OTHER, "INSTRUCTOR");

            assertThatThrownBy(() -> quizService.update(50L, updateRequest(), jwt(OTHER)))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    @Nested
    class StudentView {

        @Test
        void enrolledStudentGetsQuestionsWithTotalPoints() {
            quiz(null);
            enrolled(true);
            actAs(STUDENT, "STUDENT");

            StudentQuizResponse response = quizService.getForStudent(50L, jwt(STUDENT));

            assertThat(response.totalPoints()).isEqualTo(3);
            assertThat(response.questions()).hasSize(2);
            assertThat(response.questions().get(1).options()).hasSize(3);
        }

        @Test
        void notEnrolledIs403() {
            quiz(null);
            actAs(STUDENT, "STUDENT");

            assertThatThrownBy(() -> quizService.getForStudent(50L, jwt(STUDENT)))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Enroll to take this quiz");
        }

        @Test
        void draftCourseIsHiddenWith404() {
            quiz(null);
            course.setStatus(CourseStatus.DRAFT);
            actAs(STUDENT, "STUDENT");

            assertThatThrownBy(() -> quizService.getForStudent(50L, jwt(STUDENT)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void ownerCanPreview() {
            quiz(null);

            assertThat(quizService.getForStudent(50L, jwt(OWNER)).id()).isEqualTo(50L);
        }
    }

    @Nested
    class Start {

        @Test
        void startsNewAttemptWithExpiry() {
            quiz(10);
            enrolled(true);
            given(attemptRepository.save(any(QuizAttempt.class))).willAnswer(invocation -> {
                QuizAttempt attempt = invocation.getArgument(0);
                attempt.setId(80L);
                return attempt;
            });

            AttemptStartResponse response = quizService.startAttempt(50L, jwt(STUDENT));

            assertThat(response.attemptId()).isEqualTo(80L);
            assertThat(response.resumed()).isFalse();
            assertThat(response.expiresAt()).isEqualTo(response.startedAt().plus(Duration.ofMinutes(10)));
        }

        @Test
        void resumesOpenUnexpiredAttempt() {
            Quiz quiz = quiz(10);
            enrolled(true);
            QuizAttempt open = attempt(quiz, Instant.now().minus(Duration.ofMinutes(2)));
            given(attemptRepository.findFirstByQuizIdAndStudentKeycloakIdAndSubmittedAtIsNullOrderByStartedAtDesc(
                    50L, STUDENT)).willReturn(Optional.of(open));

            AttemptStartResponse response = quizService.startAttempt(50L, jwt(STUDENT));

            assertThat(response.attemptId()).isEqualTo(80L);
            assertThat(response.resumed()).isTrue();
            verify(attemptRepository, never()).save(any());
        }

        @Test
        void startsNewAttemptWhenOpenOneExpired() {
            Quiz quiz = quiz(10);
            enrolled(true);
            QuizAttempt expired = attempt(quiz, Instant.now().minus(Duration.ofMinutes(11)));
            given(attemptRepository.findFirstByQuizIdAndStudentKeycloakIdAndSubmittedAtIsNullOrderByStartedAtDesc(
                    50L, STUDENT)).willReturn(Optional.of(expired));
            given(attemptRepository.save(any(QuizAttempt.class))).willAnswer(invocation -> invocation.getArgument(0));

            assertThat(quizService.startAttempt(50L, jwt(STUDENT)).resumed()).isFalse();
            verify(attemptRepository).save(any(QuizAttempt.class));
        }

        @Test
        void notEnrolledIs403() {
            quiz(null);

            assertThatThrownBy(() -> quizService.startAttempt(50L, jwt(STUDENT)))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void unknownQuizIs404() {
            given(quizRepository.findWithCourseById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> quizService.startAttempt(99L, jwt(STUDENT)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class Submit {

        @Test
        void allCorrectScores100AndPasses() {
            QuizAttempt attempt = attempt(quiz(null), Instant.now());

            AttemptResultResponse result = quizService.submit(80L, answers(
                    new AnswerRequest(61L, List.of(71L)), new AnswerRequest(62L, List.of(74L, 73L))), jwt(STUDENT));

            assertThat(result.score()).isEqualByComparingTo("100.00");
            assertThat(result.passed()).isTrue();
            assertThat(result.earnedPoints()).isEqualTo(3);
            assertThat(attempt.getSubmittedAt()).isNotNull();
            assertThat(attempt.getPassed()).isTrue();
            assertThat(attempt.getAnswers()).hasSize(3);
        }

        @Test
        void partialMultipleChoiceSelectionEarnsNothingForThatQuestion() {
            attempt(quiz(null), Instant.now());

            AttemptResultResponse result = quizService.submit(80L, answers(
                    new AnswerRequest(61L, List.of(71L)), new AnswerRequest(62L, List.of(73L))), jwt(STUDENT));

            // 1 of 3 points; pass mark 60
            assertThat(result.score()).isEqualByComparingTo("33.33");
            assertThat(result.passed()).isFalse();
            assertThat(result.questions().get(1).correct()).isFalse();
            assertThat(result.questions().get(1).correctOptionIds()).containsExactly(73L, 74L);
            assertThat(result.questions().get(1).selectedOptionIds()).containsExactly(73L);
        }

        @Test
        void unansweredQuestionsAreRecordedWithoutOption() {
            QuizAttempt attempt = attempt(quiz(null), Instant.now());

            AttemptResultResponse result = quizService.submit(80L, answers(), jwt(STUDENT));

            assertThat(result.score()).isEqualByComparingTo("0.00");
            assertThat(attempt.getAnswers()).hasSize(2)
                    .allSatisfy(answer -> assertThat(answer.getSelectedOption()).isNull());
        }

        @Test
        void afterTimeLimitAndGraceScoresZeroAndFails() {
            QuizAttempt attempt = attempt(quiz(10), Instant.now().minus(Duration.ofMinutes(10)).minusSeconds(31));

            AttemptResultResponse result = quizService.submit(80L, answers(
                    new AnswerRequest(61L, List.of(71L)), new AnswerRequest(62L, List.of(73L, 74L))), jwt(STUDENT));

            assertThat(result.timedOut()).isTrue();
            assertThat(result.score()).isEqualByComparingTo("0");
            assertThat(result.passed()).isFalse();
            assertThat(attempt.getAnswers()).isEmpty();
            assertThat(attempt.getSubmittedAt()).isCloseTo(Instant.now(), within(Duration.ofSeconds(5)));
        }

        @Test
        void withinGracePeriodIsStillGraded() {
            attempt(quiz(10), Instant.now().minus(Duration.ofMinutes(10)).minusSeconds(10));

            AttemptResultResponse result = quizService.submit(80L, answers(
                    new AnswerRequest(61L, List.of(71L)), new AnswerRequest(62L, List.of(73L, 74L))), jwt(STUDENT));

            assertThat(result.timedOut()).isFalse();
            assertThat(result.passed()).isTrue();
        }

        @Test
        void someoneElsesAttemptIs403() {
            attempt(quiz(null), Instant.now());

            assertThatThrownBy(() -> quizService.submit(80L, answers(), jwt(OTHER)))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void secondSubmitIs409() {
            attempt(quiz(null), Instant.now()).setSubmittedAt(Instant.now());

            assertThatThrownBy(() -> quizService.submit(80L, answers(), jwt(STUDENT)))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void questionFromAnotherQuizIs400() {
            attempt(quiz(null), Instant.now());

            assertThatThrownBy(() -> quizService.submit(80L, answers(new AnswerRequest(999L, List.of())), jwt(STUDENT)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("999");
        }

        @Test
        void optionOfAnotherQuestionIs400() {
            attempt(quiz(null), Instant.now());

            assertThatThrownBy(() -> quizService.submit(80L, answers(new AnswerRequest(61L, List.of(73L))), jwt(STUDENT)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("73");
        }

        @Test
        void sameQuestionTwiceIs400() {
            attempt(quiz(null), Instant.now());

            assertThatThrownBy(() -> quizService.submit(80L, answers(
                    new AnswerRequest(61L, List.of(71L)), new AnswerRequest(61L, List.of(72L))), jwt(STUDENT)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("more than once");
        }

        @Test
        void unknownAttemptIs404() {
            given(attemptRepository.findWithQuizAndStudentById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> quizService.submit(99L, answers(), jwt(STUDENT)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // --- fixtures ---

    /**
     * Quiz 50 on lesson 30, pass mark 60: question 61 (SINGLE_CHOICE, 1 point, options 71 correct and 72) and
     * question 62 (MULTIPLE_CHOICE, 2 points, options 73 and 74 correct, 75 wrong).
     */
    private Quiz quiz(Integer timeLimitMinutes) {
        Quiz quiz = Quiz.builder().lesson(lesson).title("Basics").passMark(60).timeLimitMinutes(timeLimitMinutes).build();
        quiz.setId(50L);
        quiz.addQuestion(questionEntity(61L, QuestionType.SINGLE_CHOICE, 1, 1, optionEntity(71L, true),
                optionEntity(72L, false)));
        quiz.addQuestion(questionEntity(62L, QuestionType.MULTIPLE_CHOICE, 2, 2, optionEntity(73L, true),
                optionEntity(74L, true), optionEntity(75L, false)));
        given(quizRepository.findWithCourseById(50L)).willReturn(Optional.of(quiz));
        given(quizRepository.findQuestionsWithOptions(50L)).willReturn(quiz.getQuestions());
        return quiz;
    }

    private QuizAttempt attempt(Quiz quiz, Instant startedAt) {
        QuizAttempt attempt = QuizAttempt.builder().quiz(quiz).student(student).startedAt(startedAt).build();
        attempt.setId(80L);
        given(attemptRepository.findWithQuizAndStudentById(80L)).willReturn(Optional.of(attempt));
        return attempt;
    }

    private void enrolled(boolean enrolled) {
        given(enrollmentRepository.existsByStudentKeycloakIdAndCourseIdAndStatusIn(eq(STUDENT), eq(10L), any()))
                .willReturn(enrolled);
    }

    private static QuizQuestion questionEntity(Long id, QuestionType type, int points, int sortOrder,
                                               QuizOption... options) {
        QuizQuestion question = QuizQuestion.builder().questionText("Q" + id).type(type).points(points)
                .sortOrder(sortOrder).build();
        question.setId(id);
        for (QuizOption option : options) {
            question.addOption(option);
        }
        return question;
    }

    private static QuizOption optionEntity(Long id, boolean correct) {
        QuizOption option = QuizOption.builder().optionText("O" + id).correct(correct).build();
        option.setId(id);
        return option;
    }

    private static QuizCreateRequest request(QuizQuestionRequest... questions) {
        return new QuizCreateRequest("Basics", 60, null, List.of(questions));
    }

    private static QuizCreateRequest validRequest() {
        return request(question(QuestionType.SINGLE_CHOICE, 1, option("A", true), option("B", false)));
    }

    private static QuizUpdateRequest updateRequest() {
        return new QuizUpdateRequest("Renamed", 50, 5, List.of(
                question(QuestionType.TRUE_FALSE, 1, option("True", true), option("False", false))));
    }

    private static QuizQuestionRequest question(QuestionType type, Integer points, QuizOptionRequest... options) {
        return new QuizQuestionRequest("Question?", type, points, List.of(options));
    }

    private static QuizOptionRequest option(String text, boolean correct) {
        return new QuizOptionRequest(text, correct);
    }

    private static AttemptSubmitRequest answers(AnswerRequest... answers) {
        return new AttemptSubmitRequest(List.of(answers));
    }

    private static void actAs(UUID subject, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(jwt(subject), null, "ROLE_" + role));
    }

    private static Jwt jwt(UUID subject) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(subject.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }
}
