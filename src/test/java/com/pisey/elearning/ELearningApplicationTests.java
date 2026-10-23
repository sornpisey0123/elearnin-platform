package com.pisey.elearning;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Full context on a throwaway PostgreSQL: Flyway migrates, Hibernate validates, all beans wire.
 * Keycloak is not contacted at startup (JWT decoder and client token are lazy), so it need not run.
 */
@SpringBootTest(properties = "KEYCLOAK_CLIENT_SECRET=test-secret")
@Testcontainers
class ELearningApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    void contextLoads() {
    }
}
