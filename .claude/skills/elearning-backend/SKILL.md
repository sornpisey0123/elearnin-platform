---
name: elearning-backend
description: Project guide for Pisey's e-learning platform backend (Spring Boot 4, Java 25, PostgreSQL, Flyway, Keycloak, package com.pisey.elearning). Use this skill whenever the user works on this project in any way — generating or fixing entities, repositories, services, controllers, DTOs, mappers, Flyway migrations, Keycloak security, tests, Postman collections, or asks "what's next", "build phase X", "implement courses/enrollment/quizzes/reviews/reports", or mentions the LMS, e-learning, courses, lessons, enrollments or instructors — even if they don't name the skill.
---

# E-Learning Platform Backend

An LMS REST API with three roles (ADMIN, INSTRUCTOR, STUDENT). Students browse a course
catalogue, enroll, follow lessons, take quizzes and leave reviews; instructors build
courses; admins manage categories and users and see platform reports.

## Stack (Spring Boot 4 — not 3)

Java 25 · Spring Boot 4.1.x · Gradle (Groovy) · Spring Framework 7 / Security 7 ·
Jakarta EE 11 · JPA 3.2 / Hibernate 7 · JUnit 6 · Jackson 3 (`tools.jackson.*`) ·
PostgreSQL 16 on port 1302 (`elearning_db`) · Flyway · Lombok · MapStruct 1.6 ·
springdoc 3 · Keycloak 26 on port 8081 (realm `elearning`).

Boot 4 renamed several starters. Getting these wrong is the most common source of
broken builds, so always use the Boot 4 names:
`spring-boot-starter-webmvc`, `spring-boot-starter-flyway` (+ `flyway-database-postgresql`),
`spring-boot-starter-security-oauth2-resource-server`,
`spring-boot-starter-security-oauth2-client`, test starters
`spring-boot-starter-<tech>-test`, Testcontainers 2 artifacts `testcontainers-postgresql`,
`testcontainers-junit-jupiter`. Use `jakarta.*` only.

## Core rules

- **Keycloak owns identity.** The API is a JWT resource server. No passwords, no login
  endpoints, no refresh-token table. `users.keycloak_id` = `UUID(jwt.sub)`; local users are
  provisioned on first request by `CurrentUserService.getOrCreate(Jwt)`.
- **Flyway owns the schema.** `ddl-auto=validate`. Any entity change needs a new
  `V<n>__*.sql` migration — never edit an applied migration.
- **Controllers return DTOs (records), never entities.** Business logic lives in services.
- **Ownership:** instructors modify only their own courses and everything under them;
  ADMIN bypasses. Lesson content requires enrollment (or preview / owner / admin).
- Full conventions (packages, errors, pagination, testing) are in
  `references/project-context.md` — read it before writing any code for this project.

## Build process

Work in stages 0–7; each ends with a check that must pass before moving on. When the user
names a stage, asks "what's next" or seems unsure, find their stage from what exists in the
project, then read that stage's section in `references/stages.md` — it has the exact prompt,
what Claude must produce, and the check.

| Stage | Work | Reference | Check |
|---|---|---|---|
| 0 | Environment: Gradle deps, `application.properties`, empty DB, Docker | project-context.md | `./gradlew build` passes |
| 1 | JPA entities in `entity/` + `entity/enums/` | database-schema.md | Compiles |
| 2 | Flyway `V1__init_schema.sql`, `V2__seed_data.sql` | database-schema.md | App starts, validate passes |
| 3 | Keycloak realm, roles, clients, test users (manual) | keycloak-setup.md | Each test user can log in |
| 4 | Phase 1 foundation: security, errors, CurrentUser, KeycloakAdmin, storage | phases.md | `/api/me/debug-token` shows roles; user row created |
| 5 | Phases 2–9 features, one at a time | phases.md | Tests pass + phase check |
| 6 | Feature & test-case summary document | testing.md | Every endpoint has cases |
| 7 | Postman collection + environment, Newman | testing.md | Full run green on fresh DB |

Build phases in order — each depends on the previous ones:
1 Foundation → 2 Users → 3 Categories → 4 Courses → 5 Content → 6 Enrollment & Progress →
7 Quizzes → 8 Reviews → 9 Reports.

## How to work on a phase

1. Read `references/project-context.md` and the phase's section in `references/phases.md`.
2. Ask the user for (or inspect) the existing files the phase depends on, rather than
   guessing field names — invented fields are the main cause of code that won't compile.
3. Generate complete files with full paths, package declarations and imports; no `...`
   or TODOs.
4. Include tests: service tests (Mockito) and `@WebMvcTest` tests using
   `jwt().jwt(j -> j.subject(...)).authorities(new SimpleGrantedAuthority("ROLE_X"))`,
   covering happy path, 400, 403 wrong role, 403 not owner, 404, and each business rule.
5. If the change touches entities, also produce the Flyway migration.
6. End by stating the phase check and suggesting a commit.

## Reference files

- `references/stages.md` — the full stage 0–7 development guide: per stage, what the
  developer does, the prompt to use, what Claude produces, and the pass check. Read whenever
  the user mentions a stage or asks what to do next.
- `references/project-context.md` — stack, packages, conventions, security model, error
  handling, config properties, dependencies. Read for any code task.
- `references/database-schema.md` — all 14 tables, fields, constraints, indexes, and
  Flyway rules. Read for entities, migrations, repository queries.
- `references/keycloak-setup.md` — Docker, realm, roles, clients, security config, admin
  API, Swagger OAuth. Read for auth/security work or Keycloak problems.
- `references/phases.md` — endpoint-level spec and business rules for Phases 1–9.
  Read the section for the phase being built.
- `references/testing.md` — test-case catalogue and the Postman/Newman collection spec.
  Read when writing test summaries or Postman collections.
