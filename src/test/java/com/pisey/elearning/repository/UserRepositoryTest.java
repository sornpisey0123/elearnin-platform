package com.pisey.elearning.repository;

import com.pisey.elearning.entity.User;
import com.pisey.elearning.entity.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;

import static com.pisey.elearning.repository.specification.UserSpecifications.hasRole;
import static com.pisey.elearning.repository.specification.UserSpecifications.keywordMatches;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UserRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        save("Platform Admin", "admin@elearning.local", Role.ADMIN);
        save("Dara Chan", "instructor1@elearning.local", Role.INSTRUCTOR);
        save("Vanna Kim", "student1@elearning.local", Role.STUDENT);
        save("Rith Sok", "rith_100%@elearning.local", Role.STUDENT);
    }

    @Test
    void filtersByRole() {
        assertThat(emails(Specification.allOf(hasRole(Role.STUDENT), keywordMatches(null))))
                .containsExactly("rith_100%@elearning.local", "student1@elearning.local");
    }

    @Test
    void keywordMatchesNameOrEmailCaseInsensitively() {
        assertThat(emails(keywordMatches("CHAN"))).containsExactly("instructor1@elearning.local");
        assertThat(emails(keywordMatches("student1@"))).containsExactly("student1@elearning.local");
    }

    @Test
    void likeWildcardsInKeywordMatchLiterally() {
        // Unescaped, "%" would match every row and "_" any single character
        assertThat(emails(keywordMatches("%"))).containsExactly("rith_100%@elearning.local");
        assertThat(emails(keywordMatches("h_1"))).containsExactly("rith_100%@elearning.local");
    }

    @Test
    void combinesRoleAndKeyword() {
        assertThat(emails(Specification.allOf(hasRole(Role.STUDENT), keywordMatches("kim"))))
                .containsExactly("student1@elearning.local");
        assertThat(emails(Specification.allOf(hasRole(Role.ADMIN), keywordMatches("kim")))).isEmpty();
    }

    @Test
    void emailUniquenessCheckIgnoresCaseAndOwnRow() {
        User vanna = userRepository.findAll(keywordMatches("vanna")).getFirst();

        assertThat(userRepository.existsByEmailIgnoreCaseAndIdNot("ADMIN@elearning.local", vanna.getId())).isTrue();
        assertThat(userRepository.existsByEmailIgnoreCaseAndIdNot("student1@elearning.local", vanna.getId())).isFalse();
    }

    private List<String> emails(Specification<User> spec) {
        Page<User> page = userRepository.findAll(spec, PageRequest.of(0, 50, Sort.by("email")));
        return page.map(User::getEmail).getContent();
    }

    private void save(String fullName, String email, Role role) {
        userRepository.save(User.builder()
                .keycloakId(UUID.randomUUID())
                .fullName(fullName)
                .email(email)
                .role(role)
                .build());
    }
}
