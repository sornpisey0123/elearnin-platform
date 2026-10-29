package com.pisey.elearning.service.impl;

import com.pisey.elearning.config.JpaAuditingConfig;
import com.pisey.elearning.dto.quiz.AttemptResultResponse;
import com.pisey.elearning.dto.quiz.AttemptStartResponse;
import com.pisey.elearning.dto.quiz.AttemptSubmitRequest;
import com.pisey.elearning.dto.quiz.AttemptSubmitRequest.AnswerRequest;
import com.pisey.elearning.dto.quiz.QuizCreateRequest;
import com.pisey.elearning.dto.quiz.QuizOptionRequest;
import com.pisey.elearning.dto.quiz.QuizQuestionRequest;
import com.pisey.elearning.dto.quiz.QuizResponse;
import com.pisey.elearning.dto.quiz.QuizSummaryResponse;
import com.pisey.elearning.dto.quiz.QuizUpdateRequest;
import com.pisey.elearning.dto.quiz.StudentQuizResponse;
import com.pisey.elearning.entity.Category;
import com.pisey.elearning.entity.Course;
import com.pisey.elearning.entity.Enrollment;
import com.pisey.elearning.entity.Lesson;
import com.pisey.elearning.entity.Section;
import com.pisey.elearning.entity.User;
import com.pisey.elearning.entity.enums.CourseLevel;
import com.pisey.elearning.entity.enums.LessonType;
import com.pisey.elearning.entity.enums.QuestionType;
import com.pisey.elearning.entity.enums.Role;
import com.pisey.elearning.exception.ConflictException;
import com.pisey.elearning.mapper.QuizMapperImpl;
import com.pisey.elearning.repository.CategoryRepository;
import com.pisey.elearning.service.CurrentUserService;
import com.pisey.elearning.service.QuizService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/** Quiz queries (entity graph over lesson-or-section, question fetch, course list order) and cascades on PostgreSQL. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QuizServiceImpl.class, CourseAccessServiceImpl.class, QuizMapperImpl.class, JpaAuditingConfig.class})
@Testcontainers
class QuizIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private QuizService quizService;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private CurrentUserService currentUserService;

    private Jwt ownerJwt;
    private Jwt studentJwt;
    private Course course;
    private Section sectionA;
    private Section sectionB;
    private Lesson lessonA1;
    private Lesson lessonB1;

    @BeforeEach
    void setUp() {
        UUID ownerId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        User instructor = entityManager.persist(User.builder().keycloakId(ownerId).role(Role.INSTRUCTOR)
                .email("i@x").fullName("Dara").build());
        User student = entityManager.persist(User.builder().keycloakId(studentId).role(Role.STUDENT)
                .email("s@x").fullName("Vanna").build());
        Category category = categoryRepository.findBySlug("programming").orElseThrow();
        course = Course.builder().instructor(instructor).category(category).title("Java").slug("java")
                .level(CourseLevel.BEGINNER).price(new BigDecimal("19.99")).build();
        course.publish();
        entityManager.persist(course);

        // Section B comes first in the outline
        sectionA = entityManager.persist(Section.builder().course(course).title("A").sortOrder(2).build());
        sectionB = entityManager.persist(Section.builder().course(course).title("B").sortOrder(1).build());
        lessonA1 = entityManager.persist(Lesson.builder().section(sectionA).title("a1").type(LessonType.TEXT)
                .body("x").sortOrder(1).build());
        lessonB1 = entityManager.persist(Lesson.builder().section(sectionB).title("b1").type(LessonType.TEXT)
                .body("x").sortOrder(1).build());
        entityManager.persist(Enrollment.builder().student(student).course(course).pricePaid(course.getPrice()).build());
        entityManager.flush();

        ownerJwt = jwt(ownerId);
        studentJwt = jwt(studentId);
        given(currentUserService.getOrCreate(any(Jwt.class))).willReturn(student);
    }

    @Test
    void studentTakesQuizAndItLocksAfterwards() {
        QuizResponse created = quizService.createForLesson(lessonA1.getId(), quizRequest(), ownerJwt);
        entityManager.flush();
        entityManager.clear();

        StudentQuizResponse view = quizService.getForStudent(created.id(), studentJwt);
        assertThat(view.totalPoints()).isEqualTo(3);
        assertThat(view.questions()).extracting(StudentQuizResponse.QuestionView::sortOrder).containsExactly(1, 2);

        AttemptStartResponse started = quizService.startAttempt(created.id(), studentJwt);
        assertThat(quizService.startAttempt(created.id(), studentJwt).attemptId()).isEqualTo(started.attemptId());

        QuizResponse.QuestionResponse single = created.questions().get(0);
        QuizResponse.QuestionResponse multiple = created.questions().get(1);
        AttemptResultResponse result = quizService.submit(started.attemptId(), new AttemptSubmitRequest(List.of(
                new AnswerRequest(single.id(), List.of(single.options().get(0).id())),
                new AnswerRequest(multiple.id(), List.of(multiple.options().get(0).id(), multiple.options().get(1).id())))),
                studentJwt);
        entityManager.flush();

        assertThat(result.passed()).isTrue();
        assertThat(result.score()).isEqualByComparingTo("100.00");
        assertThat(jdbcTemplate.queryForObject("select count(*) from attempt_answers", Integer.class)).isEqualTo(3);
        assertThat(quizService.findMyAttempts(created.id(), studentJwt))
                .singleElement().satisfies(attempt -> assertThat(attempt.passed()).isTrue());
        assertThatThrownBy(() -> quizService.update(created.id(), updateRequest(), ownerJwt))
                .isInstanceOf(ConflictException.class);

        // A delete arrives as its own request, so nothing from the attempt is still managed
        entityManager.flush();
        entityManager.clear();
        quizService.delete(created.id(), ownerJwt);
        entityManager.flush();
        assertThat(jdbcTemplate.queryForObject("select count(*) from quiz_attempts", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from quiz_questions", Integer.class)).isZero();
    }

    @Test
    void updateReplacesQuestionsWhileThereAreNoAttempts() {
        QuizResponse created = quizService.createForSection(sectionA.getId(), quizRequest(), ownerJwt);
        entityManager.flush();
        entityManager.clear();

        QuizResponse updated = quizService.update(created.id(), updateRequest(), ownerJwt);
        entityManager.flush();
        entityManager.clear();

        assertThat(updated.sectionId()).isEqualTo(sectionA.getId());
        assertThat(quizService.getForInstructor(created.id(), ownerJwt).questions()).singleElement()
                .satisfies(question -> assertThat(question.type()).isEqualTo(QuestionType.TRUE_FALSE));
        assertThat(jdbcTemplate.queryForObject("select count(*) from quiz_options", Integer.class)).isEqualTo(2);
    }

    @Test
    void courseQuizListFollowsOutlineOrder() {
        QuizResponse sectionAQuiz = quizService.createForSection(sectionA.getId(), quizRequest(), ownerJwt);
        QuizResponse lessonA1Quiz = quizService.createForLesson(lessonA1.getId(), quizRequest(), ownerJwt);
        QuizResponse lessonB1Quiz = quizService.createForLesson(lessonB1.getId(), quizRequest(), ownerJwt);
        entityManager.flush();
        entityManager.clear();

        assertThat(quizService.findByCourse(course.getId(), studentJwt))
                .extracting(QuizSummaryResponse::id)
                .containsExactly(lessonB1Quiz.id(), lessonA1Quiz.id(), sectionAQuiz.id());
    }

    private static QuizCreateRequest quizRequest() {
        return new QuizCreateRequest("Basics", 60, 10, List.of(
                new QuizQuestionRequest("2+2?", QuestionType.SINGLE_CHOICE, 1, List.of(
                        new QuizOptionRequest("4", true), new QuizOptionRequest("5", false))),
                new QuizQuestionRequest("Even numbers?", QuestionType.MULTIPLE_CHOICE, 2, List.of(
                        new QuizOptionRequest("2", true), new QuizOptionRequest("4", true),
                        new QuizOptionRequest("5", false)))));
    }

    private static QuizUpdateRequest updateRequest() {
        return new QuizUpdateRequest("Renamed", 50, null, List.of(
                new QuizQuestionRequest("Java is typed?", QuestionType.TRUE_FALSE, 1, List.of(
                        new QuizOptionRequest("True", true), new QuizOptionRequest("False", false)))));
    }

    private static Jwt jwt(UUID subject) {
        return Jwt.withTokenValue("t").header("alg", "RS256").subject(subject.toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
    }
}
