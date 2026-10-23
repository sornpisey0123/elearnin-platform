---
name: spring-api-conventions
description: Tech stack and code structure for the e-learning Spring Boot REST API. Use when creating or editing entities, enums, DTOs, mappers, repositories, services, controllers, exceptions, pagination, or build.gradle in this project.
---

# E-learning API conventions

Follow the existing `Category` resource as the reference implementation. When adding a resource, copy its shape.

## Tech stack
- Java 25 (Gradle toolchain), Spring Boot 4.1.1, Gradle Groovy DSL with `gradlew`
- `io.spring.dependency-management` 1.1.7
- Spring Web MVC (`spring-boot-starter-webmvc`), Spring Data JPA (Hibernate), PostgreSQL
- Jakarta Bean Validation (`spring-boot-starter-validation`)
- Lombok
- springdoc-openapi 3.0.3 (Swagger UI), with `@Tag` and `@Operation` on controllers
- JPA auditing (`@EnableJpaAuditing` in `config/JpaAuditingConfig`)
- Tests: JUnit 5, `spring-boot-starter-webmvc-test`

Base package: `com.pisey.elearning`. Server port 8989. Database `elearning_db` on `localhost:1302`.

## Package structure
```
config/          @Configuration classes (JpaAuditingConfig, ...)
controller/      @RestController, mapped under /api/v1/<plural-resource>
dto/             PageResponse<T> (shared)
dto/<resource>/  XRequest, XResponse records, one subpackage per resource
entity/          JPA entities extending BaseEntity
entity/enums/    Enums stored as strings (CourseLevel, CourseStatus, Role, ...)
exception/       GlobalExceptionHandler + ResourceNotFoundException, ConflictException, BadRequestException
mapper/          @Component mapper classes (XMapper)
repository/      XRepository extends JpaRepository<X, Long>
service/         Service interfaces (XService)
service/impl/    Implementations (XServiceImpl)
utils/           Final static helper classes (SlugUtils, ...)
```
Build each new resource in this order: entity → repository → DTO records → mapper → service + impl → controller.

## Entity
- Extend `BaseEntity`, which provides `Long id` (IDENTITY), `createdAt`, `updatedAt` (auditing) and Hibernate-safe `equals`/`hashCode`. Never redeclare these fields. Never use `@Data`.
- Annotations: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @Entity`.
- `@Table(name = "<plural_snake_case>")`. Declare unique constraints in `uniqueConstraints` with names `uk_<table>_<column>`, indexes in `indexes` with names `idx_<table>_<column>`, and checks with `@CheckConstraint(name = "ck_<table>_<rule>")`.
- Every column has an explicit snake_case `@Column(name = "...")` with `length`/`nullable`, plus a matching Bean Validation annotation (`@NotBlank @Size(max = N)`, `@NotNull`, `@DecimalMin`, `@Min`).
- Long text: `columnDefinition = "text"`. Money: `BigDecimal` with `precision = 10, scale = 2`.
- Enums: `@Enumerated(EnumType.STRING)` with `length = 30`, placed in `entity/enums/`.
- Defaults: `@Builder.Default` on initialised fields (`status = CourseStatus.DRAFT`, counters `= 0`, collections `= new ArrayList<>()`).
- `@ManyToOne(fetch = FetchType.LAZY, optional = false)` + `@JoinColumn(name = "<x>_id", nullable = false)`.
- Child collections: `@OneToMany(mappedBy = ..., cascade = CascadeType.ALL, orphanRemoval = true)`, `@Setter(AccessLevel.NONE)`, and `addX` / `removeX` helpers that set both sides.
- State changes are entity methods (`publish()`, `archive()`) rather than setters called from services.

## DTO
- Java `record`s in `dto/<resource>/`. Never return entities from controllers.
- One `XRequest` for create and update, with validation (`@NotBlank`, `@Size`, `@Pattern(..., message = "...")`). Add a separate `PatchXRequest` only when partial updates are needed.
- `XResponse` includes `id`, the fields, `createdAt` and `updatedAt`.
- Paged results are returned as `PageResponse<T>` via `PageResponse.from(page)`.

## Mapper
- A `@Component` class with explicit methods. Inject it into services.
```java
@Component
public class XMapper {
    public XResponse toResponse(X x) {
        return new XResponse(x.getId(), x.getName(), x.getCreatedAt(), x.getUpdatedAt());
    }
}
```
- Request → entity is built in the service with the entity's `builder()`.

## Repository
- `public interface XRepository extends JpaRepository<X, Long>`.
- Derived query methods, including uniqueness checks for create and update:
  `existsByNameIgnoreCase(name)`, `existsByNameIgnoreCaseAndIdNot(name, id)`, `existsBySlug(slug)`, `existsBySlugAndIdNot(slug, id)`, `findBySlug(slug)`, `Page<X> findByNameContainingIgnoreCase(String name, Pageable pageable)`, `existsByCategoryId(id)` for "still in use" checks.

## Service
- An interface in `service/`, implemented in `service/impl/` with `@Service @RequiredArgsConstructor @Transactional(readOnly = true)` on the class and `@Transactional` on each write method.
- Dependencies are `private final` fields. Never use field `@Autowired`.
- Standard methods: `findAll(String search, Pageable pageable)`, `findById(Long id)`, `findBySlug(String slug)`, `create(XRequest)`, `update(Long id, XRequest)`, `delete(Long id)`.
- Use a private `getX(Long id)` helper that throws `new ResourceNotFoundException("X", "id", id)`.
- Trim input strings. Check uniqueness before saving and throw `ConflictException("X name already exists: " + name)`.
- Block deletes of in-use resources with `ConflictException`.
- On update, return `repository.saveAndFlush(entity)` so `updatedAt` is filled in the response.
- Slugs: use the given slug, or `SlugUtils.slugify(name, MAX_LENGTH)`. Throw `BadRequestException` if the result is empty.

## Controller
```java
@RestController
@RequestMapping("/api/v1/xs")
@RequiredArgsConstructor
@Tag(name = "Xs")
public class XController {
    private final XService xService;

    @GetMapping
    @Operation(summary = "List xs (paged, optional name search)")
    public PageResponse<XResponse> findAll(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return xService.findAll(search, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get x by id")
    public XResponse findById(@PathVariable Long id) { return xService.findById(id); }

    @PostMapping
    @Operation(summary = "Create x")
    public ResponseEntity<XResponse> create(@Valid @RequestBody XRequest request) {
        XResponse created = xService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update x")
    public XResponse update(@PathVariable Long id, @Valid @RequestBody XRequest request) {
        return xService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete x")
    public void delete(@PathVariable Long id) { xService.delete(id); }
}
```
Controllers only validate input and call the service. Business logic goes in the service.

## Error handling
- `GlobalExceptionHandler extends ResponseEntityExceptionHandler` returns RFC 9457 `ProblemDetail`:
  - `ResourceNotFoundException` → 404 "Resource not found"
  - `ConflictException` → 409 "Conflict"
  - `BadRequestException` → 400 "Bad request"
  - `DataIntegrityViolationException` → 409 "Data integrity violation" (logged)
  - Validation (`handleMethodArgumentNotValid`) → 400 "Validation failed" with an `errors` map of field → message
- Services throw these exceptions. Do not use `ResponseStatusException`.
- Add a new exception type only if no existing one fits, and register it in the handler.

## Domain (entities already defined)
User, RefreshToken, Category, Course, Section, Lesson, LessonResource, LessonProgress, Enrollment, Quiz, QuizQuestion, QuizOption, QuizAttempt, AttemptAnswer, Review.
Enums: Role, CourseLevel, CourseStatus, EnrollmentStatus, LessonType, QuestionType.
