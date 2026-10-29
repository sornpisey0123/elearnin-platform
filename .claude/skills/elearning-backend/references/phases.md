# Phase Specifications

Contents: 1 Foundation · 2 Users · 3 Categories · 4 Courses · 5 Content ·
6 Enrollment & Progress · 7 Quizzes · 8 Reviews · 9 Reports

---

## Phase 1 — Foundation

No feature endpoints. Build:
- `SecurityConfig`, `KeycloakProperties`, `OpenApiConfig` (see keycloak-setup.md)
- `exception/` four exceptions + `GlobalExceptionHandler`
- `dto/common/PageResponse<T>`
- `utils/SlugUtils`, `utils/SecurityUtils`
- `CurrentUserService`, `KeycloakAdminService`, `FileStorageService` (+ impls) and a
  `/files/**` resource handler
- Temporary `GET /api/me/debug-token`

**Check:** Swagger login works; debug-token shows the right `realm_access.roles`; a `users`
row appears.

## Phase 2 — Users

| Method | Path | Rules |
|---|---|---|
| GET | `/api/me` | profile: id, fullName, email, role, avatarUrl, bio |
| PUT | `/api/me` | fullName, bio (email optional); sync to Keycloak, roll back local change if Keycloak fails |
| POST | `/api/me/avatar` | multipart jpg/png/webp, ≤2MB; delete old file |
| PUT | `/api/me/password` | `{currentPassword, newPassword, confirmPassword}`; wrong current → 400; new ≠ current, ≥8 chars, confirm matches |
| GET | `/api/admin/users?role=&keyword=` | paged |
| PATCH | `/api/admin/users/{id}/role` | update Keycloak roles + local; admin can't change own role |
| PATCH | `/api/admin/users/{id}/status` | enable/disable in Keycloak + local |

**Check:** profile update changes the name in Keycloak.

## Phase 3 — Categories

| Method | Path | Rules |
|---|---|---|
| GET | `/api/categories` | with `courseCount` = PUBLISHED courses, one query |
| GET | `/api/categories/{slug}/courses` | paged published `CourseSummaryResponse` |
| POST/PUT | `/api/admin/categories[/{id}]` | slug auto; duplicate name → 409 |
| DELETE | `/api/admin/categories/{id}` | 409 if any course uses it |

`CourseSummaryResponse`: id, title, slug, thumbnailUrl, level, price, avgRating, reviewCount,
enrollmentCount, instructorName, categoryName.

**Check:** courseCount ignores drafts.

## Phase 4 — Courses

| Method | Path | Rules |
|---|---|---|
| GET | `/api/courses?keyword&categoryId&level&minPrice&maxPrice&sort` | PUBLISHED only; keyword on title OR description (case-insensitive); sort `newest` (default) / `rating` / `popular`; JPA Specifications |
| GET | `/api/courses/{slug}` | detail + instructor, category, section/lesson counts, total duration; 404 unless published (owner/admin see all) |
| GET | `/api/instructor/courses?status=` | own, all statuses |
| POST | `/api/instructor/courses` | creates DRAFT |
| PUT | `/api/instructor/courses/{id}` | owner |
| POST | `/api/instructor/courses/{id}/thumbnail` | image ≤5MB |
| POST | `/api/instructor/courses/{id}/publish` | needs ≥1 section with ≥1 lesson, else 400 with reason |
| POST | `/api/instructor/courses/{id}/unpublish` | → DRAFT; 409 if active enrollments |
| DELETE | `/api/instructor/courses/{id}` | active enrollments → archive, 200 `{archived:true}`; else hard delete 204 |

Archived courses: hidden from catalogue, still visible to enrolled students.

**Check:** deleting a course with enrollments archives it.

## Phase 5 — Content

| Method | Path | Rules |
|---|---|---|
| GET | `/api/courses/{courseId}/outline` | public; sections+lessons ordered; id, title, type, durationSeconds, preview, hasQuiz — never videoUrl/body/fileUrl |
| POST | `/api/instructor/courses/{courseId}/sections` | appended at max+1 |
| PUT/DELETE | `/api/instructor/sections/{id}` | delete re-compacts orders |
| PUT | `/api/instructor/courses/{courseId}/sections/order` | `{sectionIds}` |
| POST | `/api/instructor/sections/{sectionId}/lessons` | VIDEO needs videoUrl+duration; TEXT needs body |
| PUT/DELETE | `/api/instructor/lessons/{id}` | |
| POST | `/api/instructor/lessons/{id}/file` | for FILE lessons |
| PUT | `/api/instructor/sections/{sectionId}/lessons/order` | `{lessonIds}` |
| POST | `/api/instructor/lessons/{id}/resources` | multipart + title |
| DELETE | `/api/instructor/resources/{id}` | |
| GET | `/api/lessons/{id}` | full content; allowed if preview, enrolled (ACTIVE/COMPLETED), owner or admin; else 403 "Enroll to access this lesson" |

Reorder: submitted ids must equal the current set exactly (no extras, missing, duplicates)
else 400. Use the negative-shift technique from database-schema.md.

**Check:** reorder works; non-enrolled student gets 403 on lesson content.

## Phase 6 — Enrollment & Progress

| Method | Path | Rules |
|---|---|---|
| POST | `/api/courses/{courseId}/enroll` | STUDENT; course PUBLISHED (else 400); not own course; `pricePaid` = current price; duplicate → 409 (check + catch constraint violation); atomic `enrollmentCount + 1` |
| GET | `/api/me/courses?status=in-progress\|completed` | ACTIVE vs COMPLETED; course summary, progressPct, dates |
| POST | `/api/me/lessons/{lessonId}/complete` | must be enrolled; idempotent upsert; progressPct = done/total×100 (2 dp); 100% → `markCompleted()` |
| DELETE | `/api/me/lessons/{lessonId}/complete` | un-complete; COMPLETED → ACTIVE |
| GET | `/api/me/courses/{courseId}/continue` | first incomplete lesson by section.sortOrder then lesson.sortOrder, single query; all done → `{courseCompleted:true}` + first lesson |

**Check:** double enroll → 409; continue-learning returns the right lesson.

## Phase 7 — Quizzes

Instructor (ownership via course):
- `POST /api/instructor/lessons/{lessonId}/quizzes`, `POST /api/instructor/sections/{sectionId}/quizzes`
  Body: title, passMark 0–100, timeLimitMinutes (null or ≥1), questions[] with options[].
  Validation: ≥1 question; SINGLE_CHOICE/TRUE_FALSE exactly 1 correct; MULTIPLE_CHOICE ≥1
  correct; TRUE_FALSE exactly 2 options; sortOrder from array order.
- `GET /api/instructor/quizzes/{id}` (with answers), `PUT` (replaces questions; 409 if attempts
  exist), `DELETE`.

Student (enrolled):
- `GET /api/quizzes/{id}` — no `correct` flag (separate DTO).
- `POST /api/quizzes/{id}/attempts` — returns attemptId, startedAt, expiresAt; reuse an open
  unexpired attempt.
- `POST /api/attempts/{attemptId}/submit` `{answers:[{questionId, optionIds}]}` — owner only,
  once. Past limit +30s grace → score 0, passed false. A question scores only if the selected
  set equals the correct set exactly. score = earned/total×100; passed = score ≥ passMark.
  Response includes per-question correctness and correct option ids.
- `GET /api/quizzes/{id}/attempts/me` — newest first.

Decisions made while building (beyond the spec above):
- `GET /api/courses/{courseId}/quizzes` (token required, course visibility rules) lists quiz ids in outline
  order — the outline only has a `hasQuiz` flag, so students need it to find quizzes.
- Every question needs ≥2 options; a quiz must be worth >0 points in total; `points` defaults to 1;
  option `correct` is optional (`Boolean`, defaults to false — Jackson 3 rejects a missing primitive).
- Start attempt returns 201 for a new attempt and 200 with `resumed: true` for the open one.
- Timed-out submissions store no answers. Unanswered questions store one `attempt_answers` row with null option.
- Owner/admin can open the student view (preview) but can't start attempts (not enrolled).
- No migration: the V1 schema already had the quiz tables.

**Check:** student quiz view never exposes answers.

## Phase 8 — Reviews

| Method | Path | Rules |
|---|---|---|
| GET | `/api/courses/{courseId}/reviews` | public, newest first, with student name/avatar |
| POST | `/api/courses/{courseId}/reviews` | STUDENT with ACTIVE/COMPLETED enrollment (else 403); rating 1–5, comment ≤2000; second → 409 |
| PUT | `/api/reviews/{id}` | author |
| DELETE | `/api/reviews/{id}` | author or ADMIN |
| GET | `/api/me/courses/{courseId}/review` | mine or 404 |

After every write, recompute avgRating (2 dp) and reviewCount with one aggregate query in the
same transaction.

Decisions made while building:
- The recalculation is one native PostgreSQL `UPDATE … FROM (SELECT round(avg), count)` in
  `ReviewRepository.recalculateCourseRating`; it does not touch `courses.updated_at` (like the enrollment counter).
- Reviews of a course the caller can't see → 404 (list and create), so drafts aren't revealed.
- POST needs the STUDENT role (`@PreAuthorize`) and an ACTIVE/COMPLETED enrollment; PUT is author-only, even
  for ADMIN; DELETE is author or ADMIN.
- Blank comments are stored as null; comments are trimmed. The list is paged, default sort `createdAt` desc.
- No migration: the V1 schema already had `reviews` and the cached course columns.

**Check:** avgRating updates after create/delete.

## Phase 9 — Reports

All aggregation in SQL — never load entities to count.

Admin:
- `GET /api/admin/reports/overview` → usersByRole (zero-filled), courses {total, published,
  draft, archived}, totalEnrollments, totalRevenue (= SUM pricePaid).
- `GET /api/admin/reports/top-courses?by=enrollments|rating&limit=10` (max 50); rating ranking
  only for reviewCount ≥ 3.

Instructor (own courses):
- `GET /api/instructor/reports/courses` → per course: enrollments, completedEnrollments,
  revenue, avgRating, reviewCount, plus totals.
- `GET /api/instructor/reports/courses/{courseId}/trend?months=12` (max 24) → native query with
  `date_trunc('month', enrolled_at)` in UTC; zero-fill empty months; oldest → newest;
  label `YYYY-MM`.

**Check:** months with no enrollments show 0.
