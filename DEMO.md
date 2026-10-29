# E-Learning API — Demo Script

A 15–20 minute walkthrough of the backend: identity from Keycloak, the course catalogue,
course building, enrollment and progress, and the security model. Every step uses a request
from the **E-Learning API** Postman collection, so the demo runs the same steps as the test
suite.

Covers Phases 1–6 (foundation, users, categories, courses, content, enrollment & progress).
Quizzes, reviews and reports are not built yet.

---

## 1. Before the demo (10 minutes ahead)

### 1.1 Start the infrastructure

```bash
docker compose up -d          # Keycloak on :8081 (realm "elearning" imported on first start)
```

PostgreSQL must be running on port **1302** with database `elearning_db`.

### 1.2 Start from a clean database (recommended)

A fresh database makes counts and lists on screen predictable.

```bash
docker exec postgres-elearning psql -U user -d elearning_db \
  -c 'DROP SCHEMA public CASCADE; CREATE SCHEMA public AUTHORIZATION "user";'
```

Flyway recreates the schema (`V1__init_schema.sql`) and seeds three categories
(`V2__seed_data.sql`) when the app starts.

### 1.3 Start the app

The app needs the secret of the `elearning-backend` Keycloak client. Without it, profile
updates and admin role and status changes return 500.

Keycloak admin console → realm **elearning** → Clients → `elearning-backend` →
Credentials → copy the client secret, then:

```bash
export KEYCLOAK_CLIENT_SECRET=<secret>     # PowerShell: $env:KEYCLOAK_CLIENT_SECRET="<secret>"
./gradlew bootRun
```

Wait for `Started ELearningApplication`. The API listens on **http://localhost:8989**.

### 1.4 Prepare Postman

1. Select the **E-Learning Local** environment (top right). Check `baseUrl` is
   `http://localhost:8989`.
2. Open the **E-Learning API** collection.
3. Optional dry run: run the whole collection in the Collection Runner. It should finish
   with 0 failures. Reset the database again (1.2) and restart the app before the live demo.

### 1.5 Browser tabs to have open

| Tab | URL |
|---|---|
| Swagger UI | http://localhost:8989/swagger-ui.html |
| Keycloak admin | http://localhost:8081 (admin / admin) → realm `elearning` → Users |

### Demo accounts

| Role | Username | Password |
|---|---|---|
| Admin | `admin` | `Admin@123` |
| Instructor | `instructor1` | `Instructor@123` |
| Instructor (the "other" one) | `instructor2` | `Instructor@123` |
| Student | `student1` | `Student@123` |
| Student (not enrolled) | `student2` | `Student@123` |

---

## 2. The demo

Run the requests **in the order below**. Later requests use IDs that earlier ones save into
the environment (`categoryId`, `courseId`, `lessonId`, …). Each step lists what to send,
what to point out, and what the response shows.

### Act 1 — Identity lives in Keycloak (2 min)

**Talking point:** the API stores no passwords and has no login endpoint. Keycloak issues
JWTs and the API only validates them. A local user row is created the first time someone
calls the API.

| # | Request (folder `00 Auth`) | Show |
|---|---|---|
| 1 | `AUTH Init run id` | Keycloak realm is reachable; sets a unique `runId` for this demo |
| 2 | `AUTH Token admin` … `AUTH Token student2` (5 requests) | Body tab: plain `grant_type=password` call to Keycloak; `access_token` saved to the environment |
| 3 | `AUTH GET /api/me as student (provisions user)` | `role: STUDENT`, an `id` from the local DB — the user was created on this call |
| 4 | `SEC-02 Debug token shows roles` | The realm roles read from the JWT |

Optional: paste `{{studentToken}}` into https://jwt.io to show `sub`, `email` and
`realm_access.roles`.

### Act 2 — Admin sets up the catalogue (2 min)

| # | Request | Show |
|---|---|---|
| 5 | `02 Categories / CAT-01 Public list categories` | No token needed; the 3 seeded categories |
| 6 | `CAT-02 Admin creates category → 201` | Slug generated from the name; `courseCount: 0` |
| 7 | `CAT-03 Duplicate name → 409` | Business rule: names are unique |
| 8 | `CAT-04 Blank name → 400` | Validation errors come back as RFC 7807 `ProblemDetail` with an `errors` map |
| 9 | `CAT-06 Student creates category → 403` | Roles are enforced |

### Act 3 — Instructor builds a course (4 min)

**Talking point:** a course starts as DRAFT. It is invisible to the public until it
has content and is published.

| # | Request | Show |
|---|---|---|
| 10 | `03 Courses / CRS-01 Instructor creates course → 201 DRAFT` | `status: DRAFT`, `instructor` is the caller |
| 11 | `CRS-06 Publish without sections → 400` | "Cannot publish: add at least one section" |
| 12 | `CRS-12 Draft course detail is hidden from public → 404` | Drafts don't leak, even by slug |
| 13 | `04 Content / CNT-01`, `CNT-02` Create sections | `sortOrder` 1 and 2: sections are appended |
| 14 | `CNT-03 Publish with sections but no lessons → 400` | The second publish rule |
| 15 | `CNT-07 Create VIDEO lesson`, `CNT-08 Create TEXT preview lesson`, `CNT-09 …section 2` | One lesson is marked `preview: true` |
| 16 | `CNT-10 VIDEO lesson without videoUrl → 400` | Per-type rules: VIDEO needs a URL and duration, TEXT needs a body |
| 17 | `CNT-16 Reorder lessons → 200` | The preview lesson moves to the top |
| 18 | `CNT-21 Publish → 200 PUBLISHED` | `publishedAt` set, `lessonCount: 3` |

### Act 4 — A student browses (3 min)

| # | Request | Show |
|---|---|---|
| 19 | `05 Catalogue / CRS-16 Search by keyword …` | Published course found; the second (draft) course is not |
| 20 | `CRS-17 Filter by category and level` | Filters combine |
| 21 | `CRS-20 Sort popular …` | Sort options `newest` / `rating` / `popular` |
| 22 | `CAT-15 courseCount ignores drafts` | Category shows 1 course, not 2 |
| 23 | `04 Content / CNT-22 Public outline hides lesson content` | Titles, types and durations only; no `videoUrl` or `body` |
| 24 | `CNT-24 Student opens preview lesson … → 200` | Preview lessons are free to view |
| 25 | `CNT-23 Student opens non-preview lesson … → 403` | "Enroll to access this lesson" |

### Act 5 — Enroll and learn (4 min)

| # | Request (folder `06 Enrollment & Progress`) | Show |
|---|---|---|
| 26 | `ENR-01 Student enrolls → 201` | `ACTIVE`, `pricePaid` = current price, progress 0 |
| 27 | `ENR-02 Enroll twice → 409` | One enrollment per course |
| 28 | `ENR-04 Instructor enrolls in own course → 400` | Instructors can't enroll in their own courses |
| 29 | `ENR-07 Enrolled student opens lesson → 200` | The lesson that returned 403 in step 25 now opens |
| 30 | `ENR-08 Continue learning …` | Resumes at the first lesson in outline order |
| 31 | `ENR-09`, `ENR-12` Complete lessons | `completedLessons` 1, then 2 of 3 |
| 32 | `ENR-13 Complete last lesson → COMPLETED, 100%` | Enrollment flips to `COMPLETED` |
| 33 | `ENR-20 My courses lists the enrollment` | Student dashboard with progress |
| 34 | `ENR-24 student2 enrolls`, then `CRS-24 Unpublish with active enrollments → 409` | Can't pull a course out from under active learners |

### Act 6 — Security and ownership (2 min)

**Talking point:** role checks happen at the URL level, ownership checks happen in the
service layer, and ADMIN can bypass ownership.

| # | Request (folder `07 Security`) | Show |
|---|---|---|
| 35 | `SEC-01 No token on instructor route → 401` | |
| 36 | `SEC-03 Student on admin route → 403` | Wrong role |
| 37 | `SEC-06 instructor2 edits another's course → 403` | Right role, not the owner: "You can only manage your own courses" |
| 38 | `03 Courses / CRS-08 Admin edits any course … → 200` | Admin bypasses ownership |
| 39 | `SEC-11 Invalid token → 401` | Forged or expired tokens are rejected |

### Act 7 — Deleting keeps history (1 min)

| # | Request (folder `08 Cleanup`) | Show |
|---|---|---|
| 40 | `CRS-25 Delete course without enrollments → 204` | Really deleted |
| 41 | `CRS-27 Delete course with enrollments → 200 archived` | `{"archived": true}`: enrollment history is kept |
| 42 | `CRS-28` → 404, then `CRS-29` → 200 `ARCHIVED` | Hidden from the public, still visible to its students |
| 43 | `CAT-17 Delete category still used by a course → 409` | |

---

## 3. Closing (1 min)

Run the whole suite to show everything you demoed is automated:

- **Postman:** Collection Runner on **E-Learning API** → 136 requests, 0 failures.
- **Terminal:** Newman needs a v2.1 JSON copy of the collection:

```bash
python postman/generate_collection.py out/
npx newman run out/elearning.postman_collection.json \
  -e out/elearning-local.postman_environment.json
```

Unit and integration tests: `./gradlew test`.

Next phases: quizzes (7), reviews (8) and reports (9).

---

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| App fails: "Found non-empty schema(s) "public" but no schema history table" | Database has tables Flyway didn't create; reset it (1.2) |
| Every request: `ECONNREFUSED` | App not running, or `baseUrl` isn't `http://localhost:8989` |
| `AUTH Token …` returns 401 `invalid_grant` | Wrong password, or the realm wasn't imported; check Keycloak → Users |
| `PUT /api/me` or admin role/status change returns 500 | `KEYCLOAK_CLIENT_SECRET` missing or wrong (1.3) |
| Requests return 401 partway through the demo | Tokens expired; re-run the `00 Auth` folder |
| A request returns 404 on `{{courseId}}` etc. | Steps run out of order; re-run from Act 3 |
