# Project Context

## Package layout (base `com.pisey.elearning`)

```
config/      SecurityConfig, OpenApiConfig, JpaAuditingConfig, KeycloakProperties
controller/  REST controllers only — no business logic
dto/         Java records grouped by feature: dto/course/CourseCreateRequest.java
entity/      JPA entities + entity/enums/
exception/   custom exceptions + GlobalExceptionHandler
mapper/      MapStruct mappers (componentModel = "spring")
repository/  Spring Data repos + repository/specification/ + repository/projection/
service/     interfaces + service/impl/
utils/       SlugUtils, SecurityUtils
```

The project also has a `resources` package inside `src/main/java`. Leave it alone unless the
user says what it is for; suggest a clearer name (`storage`) if it holds file handling.

## Coding conventions

- DTOs: Java records named `XxxCreateRequest`, `XxxUpdateRequest`, `XxxResponse`,
  `XxxSummaryResponse`. Validate with `@Valid` + Bean Validation.
- Services: `@Transactional` on writes, `@Transactional(readOnly = true)` on reads.
- Pagination: `page` (0-based), `size` (default 12, max 50) → `PageResponse<T>(content, page,
  size, totalElements, totalPages)` with a `from(Page<E>, Function<E,T>)` factory.
- Avoid N+1: `@EntityGraph`, `JOIN FETCH`, or interface projections.
- Cached counters on Course (`enrollmentCount`, `reviewCount`, `avgRating`) update in the same
  transaction as the change; increments use atomic `@Modifying` queries.
- Slugs from titles via `SlugUtils`; append `-2`, `-3`… on collision.
- Files go through `FileStorageService` (local disk under `${app.storage.root}`, served at
  `/files/**`), validating content type and size; keep it swappable for S3.
- Entities: Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`, never
  `@Data`; id-based proxy-safe equals/hashCode in `BaseEntity`; associations excluded from
  toString; all `@ManyToOne` LAZY.

## URL and role conventions

| Prefix | Access |
|---|---|
| `/api/**` public GETs | catalogue, outline, category list, reviews |
| `/api/me/**` | any authenticated user, own data |
| `/api/instructor/**` | `hasRole('INSTRUCTOR')` + ownership |
| `/api/admin/**` | `hasRole('ADMIN')` |

Ownership check: `course.getInstructor().getKeycloakId()` equals the caller's `sub`, else 403.
ADMIN bypasses ownership.

## Error handling

`@RestControllerAdvice` returning `ProblemDetail` (RFC 9457):

| Exception | Status |
|---|---|
| `ResourceNotFoundException` | 404 |
| `ConflictException` | 409 |
| `ForbiddenException` | 403 |
| `BadRequestException` | 400 |
| `MethodArgumentNotValidException` | 400 with field → message map in `errors` |
| `DataIntegrityViolationException` (unique) | 409 |

401/403 from the security layer also return ProblemDetail JSON.

## Gradle dependencies (Boot 4 names)

```groovy
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3'
implementation 'org.springframework.boot:spring-boot-starter-webmvc'
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
implementation 'org.springframework.boot:spring-boot-starter-validation'
implementation 'org.springframework.boot:spring-boot-starter-flyway'
implementation 'org.flywaydb:flyway-database-postgresql'
implementation 'org.springframework.boot:spring-boot-starter-security-oauth2-resource-server'
implementation 'org.springframework.boot:spring-boot-starter-security-oauth2-client'
implementation 'org.mapstruct:mapstruct:1.6.3'
runtimeOnly 'org.postgresql:postgresql'
compileOnly 'org.projectlombok:lombok'
annotationProcessor 'org.projectlombok:lombok'
annotationProcessor 'org.projectlombok:lombok-mapstruct-binding:0.2.0'
annotationProcessor 'org.mapstruct:mapstruct-processor:1.6.3'

testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
testImplementation 'org.springframework.boot:spring-boot-starter-data-jpa-test'
testImplementation 'org.springframework.boot:spring-boot-starter-security-test'
testImplementation 'org.springframework.boot:spring-boot-testcontainers'
testImplementation 'org.testcontainers:testcontainers-postgresql'
testImplementation 'org.testcontainers:testcontainers-junit-jupiter'
testCompileOnly 'org.projectlombok:lombok'
testAnnotationProcessor 'org.projectlombok:lombok'
testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
```

Lombok must come before MapStruct in annotation processing, with the binding in between.
Do not add `keycloak-admin-client` — it pulls RESTEasy and Jackson 2, which clash with Boot 4.

## application.properties

```properties
spring.application.name=e-learning

spring.datasource.url=jdbc:postgresql://localhost:1302/elearning_db
spring.datasource.username=${DB_USERNAME:user}
spring.datasource.password=${DB_PASSWORD:password}

spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.properties.hibernate.jdbc.time_zone=UTC

spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration

spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8081/realms/elearning

spring.security.oauth2.client.registration.keycloak-admin.provider=keycloak
spring.security.oauth2.client.registration.keycloak-admin.client-id=elearning-backend
spring.security.oauth2.client.registration.keycloak-admin.client-secret=${KEYCLOAK_CLIENT_SECRET}
spring.security.oauth2.client.registration.keycloak-admin.authorization-grant-type=client_credentials
spring.security.oauth2.client.provider.keycloak.issuer-uri=http://localhost:8081/realms/elearning

keycloak.server-url=http://localhost:8081
keycloak.realm=elearning

app.storage.root=./uploads
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB

springdoc.swagger-ui.oauth.client-id=elearning-frontend
springdoc.swagger-ui.oauth.use-pkce-with-authorization-code-grant=true
```

Keep the `.properties` format — don't convert to YAML. Secrets come from environment
variables, never committed.

## Testing conventions

- Service tests: JUnit 6 + Mockito.
- Controller tests: `@WebMvcTest` +
  `.with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())).authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))`.
- Repository tests: `@DataJpaTest` (Boot 4 module `spring-boot-data-jpa-test`) with
  `@AutoConfigureTestDatabase(replace = NONE)` and Testcontainers
  `org.testcontainers.postgresql.PostgreSQLContainer` + `@ServiceConnection`.
- Cover per endpoint: happy path, 400, 403 wrong role, 403 not owner, 404, business rules.
