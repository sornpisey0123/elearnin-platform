package com.pisey.elearning.repository;

import com.pisey.elearning.entity.Category;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the Flyway migrations on a real PostgreSQL. The context only starts if
 * Hibernate's ddl-auto=validate accepts the migrated schema.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class SchemaMigrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationsApplyAndSeedCategories() {
        List<String> slugs = categoryRepository.findAll().stream()
                .map(Category::getSlug)
                .toList();

        assertThat(slugs).containsExactlyInAnyOrder("programming", "design", "business");
    }

    @Test
    void sortOrderConstraintsAreDeferrable() {
        List<String> deferrable = jdbcTemplate.queryForList(
                "select conname from pg_constraint where condeferrable and condeferred order by conname",
                String.class);

        assertThat(deferrable).containsExactly(
                "uk_lessons_section_id_sort_order",
                "uk_sections_course_id_sort_order");
    }

    @Test
    void noRefreshTokenTable() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'refresh_tokens'",
                Integer.class);

        assertThat(count).isZero();
    }
}
