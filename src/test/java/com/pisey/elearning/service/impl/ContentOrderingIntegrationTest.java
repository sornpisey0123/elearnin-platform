package com.pisey.elearning.service.impl;

import com.pisey.elearning.config.JpaAuditingConfig;
import com.pisey.elearning.dto.content.CourseOutlineResponse;
import com.pisey.elearning.dto.content.LessonCreateRequest;
import com.pisey.elearning.dto.content.SectionCreateRequest;
import com.pisey.elearning.dto.content.SectionResponse;
import com.pisey.elearning.entity.Category;
import com.pisey.elearning.entity.Course;
import com.pisey.elearning.entity.User;
import com.pisey.elearning.entity.enums.CourseLevel;
import com.pisey.elearning.entity.enums.LessonType;
import com.pisey.elearning.entity.enums.Role;
import com.pisey.elearning.mapper.ContentMapperImpl;
import com.pisey.elearning.repository.CategoryRepository;
import com.pisey.elearning.repository.CourseRepository;
import com.pisey.elearning.repository.UserRepository;
import com.pisey.elearning.service.ContentService;
import com.pisey.elearning.service.FileStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real service against PostgreSQL with each call committing (no test transaction), so the
 * deferred {@code (parent, sort_order)} unique constraints are actually checked.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ContentServiceImpl.class, CourseAccessServiceImpl.class, ContentMapperImpl.class, JpaAuditingConfig.class})
@Testcontainers
class ContentOrderingIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private ContentService contentService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private FileStorageService fileStorageService;

    private Jwt owner;
    private Long courseId;

    @BeforeEach
    void setUp() {
        UUID keycloakId = UUID.randomUUID();
        User instructor = userRepository.save(User.builder().keycloakId(keycloakId).role(Role.INSTRUCTOR)
                .email(keycloakId + "@x").fullName("Dara").build());
        Category category = categoryRepository.findBySlug("programming").orElseThrow();
        courseId = courseRepository.save(Course.builder().instructor(instructor).category(category).title("Java")
                .slug("java-" + keycloakId).level(CourseLevel.BEGINNER).price(BigDecimal.TEN).build()).getId();

        owner = Jwt.withTokenValue("t").header("alg", "RS256").subject(keycloakId.toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(owner, null, "ROLE_INSTRUCTOR"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        courseRepository.deleteById(courseId);
    }

    @Test
    void reorderSectionsCommitsWithSwappedPositions() {
        Long a = addSection("A");
        Long b = addSection("B");
        Long c = addSection("C");

        List<SectionResponse> result = contentService.reorderSections(courseId, List.of(c, a, b), owner);

        assertThat(result).extracting(SectionResponse::id).containsExactly(c, a, b);
        assertThat(sectionOrderInDb()).containsExactly("C:1", "A:2", "B:3");
    }

    @Test
    void reorderLessonsCommits() {
        Long section = addSection("A");
        Long l1 = addLesson(section, "one");
        Long l2 = addLesson(section, "two");
        Long l3 = addLesson(section, "three");

        contentService.reorderLessons(section, List.of(l3, l2, l1), owner);

        assertThat(jdbcTemplate.queryForList(
                "select title || ':' || sort_order from lessons where section_id = ? order by sort_order",
                String.class, section)).containsExactly("three:1", "two:2", "one:3");
    }

    @Test
    void deleteSectionRecompactsRemainingOrder() {
        addSection("A");
        Long b = addSection("B");
        addSection("C");
        addSection("D");

        contentService.deleteSection(b, owner);

        assertThat(sectionOrderInDb()).containsExactly("A:1", "C:2", "D:3");
    }

    @Test
    void deleteLessonRecompactsAndOutlineFollowsOrder() {
        Long section = addSection("A");
        addLesson(section, "one");
        Long two = addLesson(section, "two");
        addLesson(section, "three");

        contentService.deleteLesson(two, owner);

        CourseOutlineResponse outline = contentService.getOutline(courseId, owner);
        assertThat(outline.sections().getFirst().lessons())
                .extracting(CourseOutlineResponse.LessonOutline::title)
                .containsExactly("one", "three");
        assertThat(jdbcTemplate.queryForList(
                "select sort_order from lessons where section_id = ? order by sort_order", Integer.class, section))
                .containsExactly(1, 2);
    }

    @Test
    void outlineOrdersSectionsAndLessonsAfterReorder() {
        Long a = addSection("A");
        Long b = addSection("B");
        addLesson(b, "b1");
        Long b2 = addLesson(b, "b2");
        addLesson(a, "a1");
        contentService.reorderSections(courseId, List.of(b, a), owner);
        contentService.reorderLessons(b, List.of(b2, b2 - 1), owner);

        CourseOutlineResponse outline = contentService.getOutline(courseId, owner);

        assertThat(outline.sections()).extracting(CourseOutlineResponse.SectionOutline::title).containsExactly("B", "A");
        assertThat(outline.sections().getFirst().lessons())
                .extracting(CourseOutlineResponse.LessonOutline::title).containsExactly("b2", "b1");
    }

    private Long addSection(String title) {
        return contentService.createSection(courseId, new SectionCreateRequest(title), owner).id();
    }

    private Long addLesson(Long sectionId, String title) {
        return contentService.createLesson(sectionId,
                new LessonCreateRequest(title, LessonType.TEXT, null, null, "body", false), owner).id();
    }

    private List<String> sectionOrderInDb() {
        return jdbcTemplate.queryForList(
                "select title || ':' || sort_order from sections where course_id = ? order by sort_order",
                String.class, courseId);
    }
}
