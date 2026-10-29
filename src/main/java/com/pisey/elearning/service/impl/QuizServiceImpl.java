package com.pisey.elearning.service.impl;

import com.pisey.elearning.dto.quiz.AttemptResultResponse;
import com.pisey.elearning.dto.quiz.AttemptResultResponse.QuestionResult;
import com.pisey.elearning.dto.quiz.AttemptStartResponse;
import com.pisey.elearning.dto.quiz.AttemptSubmitRequest;
import com.pisey.elearning.dto.quiz.AttemptSubmitRequest.AnswerRequest;
import com.pisey.elearning.dto.quiz.AttemptSummaryResponse;
import com.pisey.elearning.dto.quiz.QuizCreateRequest;
import com.pisey.elearning.dto.quiz.QuizOptionRequest;
import com.pisey.elearning.dto.quiz.QuizQuestionRequest;
import com.pisey.elearning.dto.quiz.QuizResponse;
import com.pisey.elearning.dto.quiz.QuizSummaryResponse;
import com.pisey.elearning.dto.quiz.QuizUpdateRequest;
import com.pisey.elearning.dto.quiz.StudentQuizResponse;
import com.pisey.elearning.entity.AttemptAnswer;
import com.pisey.elearning.entity.BaseEntity;
import com.pisey.elearning.entity.Course;
import com.pisey.elearning.entity.Lesson;
import com.pisey.elearning.entity.Quiz;
import com.pisey.elearning.entity.QuizAttempt;
import com.pisey.elearning.entity.QuizOption;
import com.pisey.elearning.entity.QuizQuestion;
import com.pisey.elearning.entity.Section;
import com.pisey.elearning.exception.BadRequestException;
import com.pisey.elearning.exception.ConflictException;
import com.pisey.elearning.exception.ForbiddenException;
import com.pisey.elearning.exception.ResourceNotFoundException;
import com.pisey.elearning.mapper.QuizMapper;
import com.pisey.elearning.repository.CourseRepository;
import com.pisey.elearning.repository.LessonRepository;
import com.pisey.elearning.repository.QuizAttemptRepository;
import com.pisey.elearning.repository.QuizRepository;
import com.pisey.elearning.repository.SectionRepository;
import com.pisey.elearning.service.CourseAccessService;
import com.pisey.elearning.service.CurrentUserService;
import com.pisey.elearning.service.QuizService;
import com.pisey.elearning.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuizServiceImpl implements QuizService {

    static final Duration SUBMIT_GRACE = Duration.ofSeconds(30);

    private final QuizRepository quizRepository;
    private final QuizAttemptRepository attemptRepository;
    private final CourseRepository courseRepository;
    private final SectionRepository sectionRepository;
    private final LessonRepository lessonRepository;
    private final CourseAccessService courseAccessService;
    private final CurrentUserService currentUserService;
    private final QuizMapper quizMapper;

    // --- instructor ---

    @Override
    @Transactional
    public QuizResponse createForLesson(Long lessonId, QuizCreateRequest request, Jwt caller) {
        Lesson lesson = lessonRepository.findWithCourseById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", lessonId));
        courseAccessService.requireOwnerOrAdmin(lesson.getSection().getCourse(), caller);

        Quiz quiz = Quiz.builder().lesson(lesson).build();
        applySettings(quiz, request.title(), request.passMark(), request.timeLimitMinutes(), request.questions());
        return quizMapper.toResponse(quizRepository.save(quiz));
    }

    @Override
    @Transactional
    public QuizResponse createForSection(Long sectionId, QuizCreateRequest request, Jwt caller) {
        Section section = sectionRepository.findWithCourseById(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Section", "id", sectionId));
        courseAccessService.requireOwnerOrAdmin(section.getCourse(), caller);

        Quiz quiz = Quiz.builder().section(section).build();
        applySettings(quiz, request.title(), request.passMark(), request.timeLimitMinutes(), request.questions());
        return quizMapper.toResponse(quizRepository.save(quiz));
    }

    @Override
    public QuizResponse getForInstructor(Long quizId, Jwt caller) {
        Quiz quiz = getOwnedQuiz(quizId, caller);
        quizRepository.findQuestionsWithOptions(quizId);
        return quizMapper.toResponse(quiz);
    }

    @Override
    @Transactional
    public QuizResponse update(Long quizId, QuizUpdateRequest request, Jwt caller) {
        Quiz quiz = getOwnedQuiz(quizId, caller);
        if (attemptRepository.existsByQuizId(quizId)) {
            throw new ConflictException("Quiz already has attempts; create a new quiz instead of editing this one");
        }

        // Orphan removal deletes the old questions and their options
        new ArrayList<>(quiz.getQuestions()).forEach(quiz::removeQuestion);
        applySettings(quiz, request.title(), request.passMark(), request.timeLimitMinutes(), request.questions());
        return quizMapper.toResponse(quizRepository.saveAndFlush(quiz));
    }

    @Override
    @Transactional
    public void delete(Long quizId, Jwt caller) {
        // Attempts and their answers go with it (ON DELETE CASCADE)
        quizRepository.delete(getOwnedQuiz(quizId, caller));
    }

    // --- students ---

    @Override
    public List<QuizSummaryResponse> findByCourse(Long courseId, Jwt caller) {
        courseRepository.findWithInstructorAndCategoryById(courseId)
                .filter(course -> courseAccessService.isVisible(course, caller))
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", courseId));
        return quizRepository.findByCourseId(courseId).stream().map(quizMapper::toSummary).toList();
    }

    @Override
    public StudentQuizResponse getForStudent(Long quizId, Jwt caller) {
        Quiz quiz = getQuiz(quizId);
        Course course = quiz.getCourse();
        if (!courseAccessService.isOwnerOrAdmin(course, caller)) {
            requireEnrolled(course, caller, quizId);
        }
        quizRepository.findQuestionsWithOptions(quizId);
        return quizMapper.toStudentResponse(quiz);
    }

    @Override
    @Transactional
    public AttemptStartResponse startAttempt(Long quizId, Jwt caller) {
        Quiz quiz = getQuiz(quizId);
        requireEnrolled(quiz.getCourse(), caller, quizId);

        Instant now = Instant.now();
        QuizAttempt open = attemptRepository
                .findFirstByQuizIdAndStudentKeycloakIdAndSubmittedAtIsNullOrderByStartedAtDesc(
                        quizId, SecurityUtils.keycloakId(caller))
                .filter(attempt -> expiresAt(attempt) == null || now.isBefore(expiresAt(attempt)))
                .orElse(null);
        if (open != null) {
            return new AttemptStartResponse(open.getId(), quizId, open.getStartedAt(), expiresAt(open), true);
        }

        QuizAttempt attempt = attemptRepository.save(QuizAttempt.builder()
                .quiz(quiz)
                .student(currentUserService.getOrCreate(caller))
                .startedAt(now)
                .build());
        return new AttemptStartResponse(attempt.getId(), quizId, attempt.getStartedAt(), expiresAt(attempt), false);
    }

    @Override
    @Transactional
    public AttemptResultResponse submit(Long attemptId, AttemptSubmitRequest request, Jwt caller) {
        QuizAttempt attempt = attemptRepository.findWithQuizAndStudentById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt", "id", attemptId));
        if (!attempt.getStudent().getKeycloakId().equals(SecurityUtils.keycloakId(caller))) {
            throw new ForbiddenException("You can only submit your own attempts");
        }
        if (attempt.getSubmittedAt() != null) {
            throw new ConflictException("This attempt was already submitted");
        }

        Quiz quiz = attempt.getQuiz();
        List<QuizQuestion> questions = quizRepository.findQuestionsWithOptions(quiz.getId());
        Map<Long, Set<Long>> selections = selectionsByQuestion(request.answers(), questions);
        Instant now = Instant.now();
        Instant expiresAt = expiresAt(attempt);
        boolean timedOut = expiresAt != null && now.isAfter(expiresAt.plus(SUBMIT_GRACE));

        int totalPoints = 0;
        int earnedPoints = 0;
        List<QuestionResult> results = new ArrayList<>();
        for (QuizQuestion question : questions) {
            Set<Long> selected = selections.getOrDefault(question.getId(), Set.of());
            Set<Long> correctIds = question.getOptions().stream()
                    .filter(QuizOption::isCorrect)
                    .map(BaseEntity::getId)
                    .collect(Collectors.toCollection(TreeSet::new));
            boolean correct = !timedOut && selected.equals(correctIds);
            int earned = correct ? question.getPoints() : 0;
            totalPoints += question.getPoints();
            earnedPoints += earned;
            results.add(new QuestionResult(question.getId(), correct, question.getPoints(), earned,
                    List.copyOf(selected), List.copyOf(correctIds)));
            if (!timedOut) {
                recordAnswers(attempt, question, selected);
            }
        }

        BigDecimal score = totalPoints == 0 ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(earnedPoints * 100L).divide(BigDecimal.valueOf(totalPoints), 2, RoundingMode.HALF_UP);
        boolean passed = !timedOut && score.compareTo(BigDecimal.valueOf(quiz.getPassMark())) >= 0;
        attempt.setScore(score);
        attempt.setPassed(passed);
        attempt.setSubmittedAt(now);
        attemptRepository.save(attempt);

        return new AttemptResultResponse(attempt.getId(), quiz.getId(), score, passed, timedOut, earnedPoints,
                totalPoints, attempt.getStartedAt(), now, results);
    }

    @Override
    public List<AttemptSummaryResponse> findMyAttempts(Long quizId, Jwt caller) {
        getQuiz(quizId);
        return attemptRepository
                .findByQuizIdAndStudentKeycloakIdOrderByStartedAtDescIdDesc(quizId, SecurityUtils.keycloakId(caller))
                .stream()
                .map(quizMapper::toAttemptSummary)
                .toList();
    }

    // --- helpers ---

    private Quiz getQuiz(Long quizId) {
        return quizRepository.findWithCourseById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz", "id", quizId));
    }

    private Quiz getOwnedQuiz(Long quizId, Jwt caller) {
        Quiz quiz = getQuiz(quizId);
        courseAccessService.requireOwnerOrAdmin(quiz.getCourse(), caller);
        return quiz;
    }

    /** 404 if the caller can't even see the course (so it isn't revealed), 403 if they can but aren't enrolled. */
    private void requireEnrolled(Course course, Jwt caller, Long quizId) {
        if (courseAccessService.isEnrolled(course, caller)) {
            return;
        }
        if (!courseAccessService.isVisible(course, caller)) {
            throw new ResourceNotFoundException("Quiz", "id", quizId);
        }
        throw new ForbiddenException("Enroll to take this quiz");
    }

    /** Validates the questions, then sets the quiz's fields and appends the questions in request order. */
    private static void applySettings(Quiz quiz, String title, Integer passMark, Integer timeLimitMinutes,
                                      List<QuizQuestionRequest> questions) {
        for (int i = 0; i < questions.size(); i++) {
            validateQuestion(i, questions.get(i));
        }
        if (questions.stream().mapToInt(QuizServiceImpl::pointsOf).sum() == 0) {
            throw new BadRequestException("At least one question must be worth points");
        }

        quiz.setTitle(title.trim());
        quiz.setPassMark(passMark);
        quiz.setTimeLimitMinutes(timeLimitMinutes);
        for (int i = 0; i < questions.size(); i++) {
            QuizQuestionRequest request = questions.get(i);
            QuizQuestion question = QuizQuestion.builder()
                    .questionText(request.questionText().trim())
                    .type(request.type())
                    .points(pointsOf(request))
                    .sortOrder(i + 1)
                    .build();
            request.options().forEach(option -> question.addOption(QuizOption.builder()
                    .optionText(option.optionText().trim())
                    .correct(isCorrect(option))
                    .build()));
            quiz.addQuestion(question);
        }
    }

    private static void validateQuestion(int index, QuizQuestionRequest question) {
        long correct = question.options().stream().filter(QuizServiceImpl::isCorrect).count();
        String rule = switch (question.type()) {
            case SINGLE_CHOICE -> correct == 1 ? null : "SINGLE_CHOICE needs exactly one correct option";
            case MULTIPLE_CHOICE -> correct >= 1 ? null : "MULTIPLE_CHOICE needs at least one correct option";
            case TRUE_FALSE -> question.options().size() == 2 && correct == 1 ? null
                    : "TRUE_FALSE needs exactly two options, one of them correct";
        };
        if (rule != null) {
            throw new BadRequestException("questions[%d]: %s".formatted(index, rule));
        }
    }

    private static boolean isCorrect(QuizOptionRequest option) {
        return Boolean.TRUE.equals(option.correct());
    }

    private static int pointsOf(QuizQuestionRequest question) {
        return question.points() == null ? 1 : question.points();
    }

    private static Instant expiresAt(QuizAttempt attempt) {
        Integer limit = attempt.getQuiz().getTimeLimitMinutes();
        return limit == null ? null : attempt.getStartedAt().plus(Duration.ofMinutes(limit));
    }

    /**
     * Selected option ids per question. 400 for a question that isn't in the quiz, a question answered twice, or an
     * option that doesn't belong to its question.
     */
    private static Map<Long, Set<Long>> selectionsByQuestion(List<AnswerRequest> answers,
                                                             List<QuizQuestion> questions) {
        Map<Long, QuizQuestion> byId = questions.stream()
                .collect(Collectors.toMap(BaseEntity::getId, Function.identity()));
        Map<Long, Set<Long>> selections = new HashMap<>();
        for (AnswerRequest answer : answers) {
            QuizQuestion question = byId.get(answer.questionId());
            if (question == null) {
                throw new BadRequestException("Question %d is not part of this quiz".formatted(answer.questionId()));
            }
            Set<Long> optionIds = answer.optionIds() == null ? new TreeSet<>() : new TreeSet<>(answer.optionIds());
            Set<Long> validIds = question.getOptions().stream().map(BaseEntity::getId).collect(Collectors.toSet());
            if (!validIds.containsAll(optionIds)) {
                throw new BadRequestException("Question %d has no option(s) %s".formatted(
                        question.getId(), optionIds.stream().filter(id -> !validIds.contains(id)).toList()));
            }
            if (selections.put(question.getId(), optionIds) != null) {
                throw new BadRequestException("Question %d is answered more than once".formatted(question.getId()));
            }
        }
        return selections;
    }

    /** One row per selected option; an unanswered question gets one row with no option. */
    private static void recordAnswers(QuizAttempt attempt, QuizQuestion question, Set<Long> selected) {
        if (selected.isEmpty()) {
            attempt.addAnswer(AttemptAnswer.builder().question(question).build());
            return;
        }
        question.getOptions().stream()
                .filter(option -> selected.contains(option.getId()))
                .forEach(option -> attempt.addAnswer(
                        AttemptAnswer.builder().question(question).selectedOption(option).build()));
    }
}
