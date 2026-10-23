# Development Stages 0–7

Contents: Overview · Stage 0 Environment · Stage 1 Entities · Stage 2 Migrations ·
Stage 3 Keycloak · Stage 4 Foundation · Stage 5 Features · Stage 6 Test-case summary ·
Stage 7 Postman · Working rules

Each stage has: what the developer does, the prompt to send, what Claude must do, and the
check that must pass before the next stage. When the user names a stage (or asks "what's
next"), read that stage's section and follow it.

## Overview

| Stage | What you do | Prompt to use | Check |
|---|---|---|---|
| 0 | Prepare the environment | None (manual) | `./gradlew build` passes |
| 1 | Generate the entities | Stage 1 prompt | Project compiles |
| 2 | Create the database schema | Stage 2 prompt (Flyway) | App starts, Flyway applies 2 migrations, validate passes |
| 3 | Set up Keycloak | None (manual) — Stage 3 prompt for guidance | Each test user can log in |
| 4 | Build the foundation | Stage 4 prompt (Phase 1) | `/api/me/debug-token` shows roles; `users` row created |
| 5 | Build the features | Stage 5 prompt, Phases 2–9 one at a time | Tests pass + phase check |
| 6 | Write the feature and test-case summary | Stage 6 prompt | Every endpoint has test cases |
| 7 | Build the Postman collection | Stage 7 prompt | Full Newman run green on a fresh DB |

Commit to Git after every stage passes its check.

---

## Stage 0 — Prepare the environment

**Developer does (manual):**
1. Gradle project with the dependencies from `project-context.md` (Boot 4 starter names).
2. `src/main/resources/application.properties` from `project-context.md`
   (`ddl-auto=validate`, Flyway enabled).
3. PostgreSQL running on port 1302 with an **empty** `elearning_db`.
4. Docker installed (Keycloak, Testcontainers).
5. Git repository initialised.

**Optional prompt:**
```text
Check my stage 0 setup against the elearning-backend skill.
[paste build.gradle and application.properties]
```

**Claude does:** compare against `project-context.md`; flag wrong Boot 4 starter names, missing
dependencies, `ddl-auto` not `validate`, hard-coded secrets, YAML instead of properties.

**Check:** `./gradlew build` succeeds.

---

## Stage 1 — Generate the entities

**Prompt:**
```text
Build stage 1: generate all JPA entities, enums, BaseEntity and JpaAuditingConfig
following the elearning-backend skill's database schema.
```

**Claude does:**
- Read `database-schema.md` and the entity conventions in `project-context.md`.
- Generate in `com.pisey.elearning.entity`: `BaseEntity`, User, Category, Course, Section,
  Lesson, LessonResource, Enrollment, LessonProgress, Quiz, QuizQuestion, QuizOption,
  QuizAttempt, AttemptAnswer, Review.
- Enums in `entity/enums/`: Role, CourseLevel, CourseStatus, LessonType, EnrollmentStatus,
  QuestionType.
- `config/JpaAuditingConfig` with `@EnableJpaAuditing`.
- Keycloak version of User: `keycloakId` UUID unique, **no** password field, **no**
  RefreshToken entity.
- Constraints via `@Table(uniqueConstraints, indexes)` and Hibernate `@Check`.
- Generate only entities — no repositories, services, controllers or migrations.

**Check:** project compiles. (The app won't start yet — no tables exist.)

---

## Stage 2 — Create the database schema

**Prompt:**
```text
Build stage 2: generate V1__init_schema.sql and V2__seed_data.sql from my entities,
then list every spot where the SQL and the annotations could disagree.
[paste entity files if Claude can't read the project]
```

**Claude does:**
- Read the actual entity files (ask for them if not visible) and `database-schema.md`.
- `src/main/resources/db/migration/V1__init_schema.sql`:
  - one `CREATE TABLE` per entity, columns/types/nullability/defaults matching the entities
    exactly (type mapping table in `database-schema.md`);
  - tables in dependency order;
  - all FKs with explicit `ON DELETE` (CASCADE for compositions, RESTRICT for users and
    `courses.category_id`);
  - all UNIQUE constraints, with `(course_id, sort_order)` and `(section_id, sort_order)`
    `DEFERRABLE INITIALLY DEFERRED`;
  - all CHECKs (price ≥ 0, pass_mark 0–100, rating 1–5, quiz exactly one parent);
  - all indexes + the GIN full-text index on courses.
- `V2__seed_data.sql`: categories Programming, Design, Business. No users.
- Finish with a list of possible entity/SQL mismatches to double-check.

**If it fails:** "non-empty schema" → drop and recreate `elearning_db` (tables were made by an
earlier `ddl-auto=update`). "Schema-validation: wrong column type" → fix the SQL or the
annotation so they match, then recreate the DB (in dev only; never edit an applied migration
once shared).

**Check:** app starts, Flyway logs "Successfully applied 2 migrations", Hibernate validation passes.

---

## Stage 3 — Set up Keycloak

**Developer does (manual), following `keycloak-setup.md`:**
1. `docker-compose.yml` with Keycloak on port 8081 → `docker compose up -d`.
2. Realm `elearning`; realm roles ADMIN, INSTRUCTOR, STUDENT; STUDENT as default role;
   user registration on.
3. Clients `elearning-frontend` (public, PKCE), `elearning-backend` (confidential, service
   account with realm-management roles), `elearning-password-check` (public, direct access
   grants only).
4. Test users: 1 admin, 2 instructors, 2 students, with roles assigned.
5. Copy the `elearning-backend` secret into the `KEYCLOAK_CLIENT_SECRET` env variable.
6. Export the realm into `./keycloak/`.

**Guidance prompt:**
```text
Walk me through stage 3 Keycloak setup step by step and give me the docker-compose.yml.
```

**Claude does:** give the compose file and the console steps from `keycloak-setup.md`, one
section at a time, with exact field values.

**Check:** each test user can log in at `http://localhost:8081/realms/elearning/account`.

---

## Stage 4 — Build the foundation (Phase 1)

**Prompt:**
```text
Build phase 1 (foundation) from the elearning-backend skill, including tests.
```

**Claude does:** implement Phase 1 from `phases.md`:
SecurityConfig (JWT + realm role converter), KeycloakProperties, OpenApiConfig (OAuth2),
exceptions + GlobalExceptionHandler, PageResponse, SlugUtils, SecurityUtils,
CurrentUserService, KeycloakAdminService (RestClient + client credentials), FileStorageService,
`/files/**` handler, temporary `GET /api/me/debug-token`. List any properties/dependencies to add.

**Check — do not continue until all three pass:**
1. Log in through Swagger UI (`http://localhost:8080/swagger-ui.html` → Authorize).
2. `GET /api/me/debug-token` shows the right `realm_access.roles`.
3. A row appears in the `users` table.

Common failures are in the troubleshooting section of `keycloak-setup.md`.

---

## Stage 5 — Build the features (Phases 2–9)

**Prompt (one phase per request):**
```text
Build phase <N> (<name>) from the elearning-backend skill, with tests.
Here are the existing files it depends on: [paste, or let Claude Code read them]
```

Order: 2 Users → 3 Categories → 4 Courses → 5 Content → 6 Enrollment & Progress →
7 Quizzes → 8 Reviews → 9 Reports.

**Claude does:** follow "How to work on a phase" in SKILL.md and the phase section in
`phases.md`: DTOs, mapper, repository methods, service + impl, controller(s), tests, and a
Flyway migration if entities change.

**After each phase the developer:** runs `./gradlew test`, tries 2–3 endpoints in Swagger,
commits.

| Phase | Check before moving on |
|---|---|
| 2 Users | Profile update changes the name in Keycloak too |
| 3 Categories | `courseCount` counts only published courses |
| 4 Courses | Deleting a course with enrollments archives it |
| 5 Content | Reorder works; non-enrolled student gets 403 on a lesson |
| 6 Enrollment | Enrolling twice → 409; continue-learning returns the right lesson |
| 7 Quizzes | Student view never shows correct answers |
| 8 Reviews | `avgRating` updates after create/delete |
| 9 Reports | Months with no enrollments show 0 |

After Phase 9, remove the temporary `/api/me/debug-token` endpoint.

---

## Stage 6 — Write the feature and test-case summary

**Prompt:**
```text
Build stage 6: create FEATURES_AND_TEST_CASES.md from my implemented controllers.
[paste controllers, or let Claude Code read them]
```

**Claude does:** read the real controllers (not just the spec) and `testing.md`; produce the
document with: feature summary per module, endpoint table per module, test-case tables with
IDs (USR, CAT, CRS, CNT, ENR, QZ, REV, RPT, SEC), all mandatory business-rule cases, and the
end-to-end journey. Flag any spec rule from `phases.md` the controllers don't implement.

**Check:** every endpoint has happy path, 400, 401, 403 (role and ownership), 404 cases, and
every row of the business-rule table in `testing.md` is covered.

---

## Stage 7 — Build the Postman collection

**Prompt:**
```text
Build stage 7: generate the Postman collection and environment from
FEATURES_AND_TEST_CASES.md, then give me the Newman command.
```

**Claude does:** follow the Postman section of `testing.md`:
- `elearning.postman_collection.json` (v2.1) and `elearning-local.postman_environment.json`;
- folders 00 Auth → 09 Cleanup in order, tokens via password grant on
  `elearning-password-check`;
- per request: Bearer token for the right role, `pm.test` assertions, id chaining with
  `pm.environment.set`, `{{$timestamp}}` names, names prefixed with the test-case ID;
- Newman + htmlextra command.

**Developer does:** recreate `elearning_db`, restart the app, import both files into Postman,
run the collection, then run Newman.

**Check:** full collection green in Postman Runner and Newman on a fresh database.

---

## Working rules for every stage

- Never skip a stage's check — later stages depend on it.
- Generate complete files with full paths; no `...` or TODOs.
- Use the real project files when they exist; never invent fields.
- If a decision changes (new table, new rule), update the skill's reference files so later
  stages stay consistent.
- Possible Stage 8 when the user asks: Dockerfile, full docker-compose (app + PostgreSQL +
  Keycloak), CI running `./gradlew test` and Newman on every push.
