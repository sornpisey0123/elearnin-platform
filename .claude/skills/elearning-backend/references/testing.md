# Testing, Test Cases and Postman

## Test-case document (`FEATURES_AND_TEST_CASES.md`)

Structure:
1. One-paragraph feature summary per module and who uses it.
2. Endpoint table per module: Method | Path | Role | Description.
3. Test-case table per module:
   ID | Scenario | Role | Preconditions | Request | Expected status | Expected result.
   ID prefixes: `USR`, `CAT`, `CRS`, `CNT`, `ENR`, `QZ`, `REV`, `RPT`, `SEC`.
4. End-to-end "happy journey" in execution order.

Every endpoint needs: happy path, 400 validation, 401 unauthenticated, 403 wrong role,
403 not owner (where applicable), 404.

## Business-rule cases that must exist

| ID | Rule | Expected |
|---|---|---|
| CAT-xx | Delete category in use | 409 |
| CAT-xx | courseCount ignores drafts | count excludes DRAFT |
| CRS-xx | Publish without lessons | 400 |
| CRS-xx | Delete with active enrollments | 200 `{archived:true}` |
| CRS-xx | Delete without enrollments | 204 |
| CRS-xx | Search / filter / each sort | correct order |
| CNT-xx | Reorder with wrong id set | 400 |
| CNT-xx | Outline hides content fields | no videoUrl/body/fileUrl |
| CNT-xx | Lesson content, not enrolled | 403 |
| CNT-xx | Preview lesson, not enrolled | 200 |
| ENR-xx | Enroll twice | 409 |
| ENR-xx | Enroll in draft course | 400 |
| ENR-xx | Instructor enrolls in own course | 400/403 |
| ENR-xx | Complete all lessons | status COMPLETED, progress 100 |
| ENR-xx | Continue learning | first incomplete lesson |
| QZ-xx | Student quiz view | no `correct` field |
| QZ-xx | All correct / some wrong | passed true / false |
| QZ-xx | Multiple-choice partial selection | 0 points for that question |
| QZ-xx | Submit after time limit | score 0, passed false |
| QZ-xx | Edit quiz with attempts | 409 |
| REV-xx | Second review | 409 |
| REV-xx | Not enrolled | 403 |
| REV-xx | avgRating after create/delete | recalculated |
| RPT-xx | Trend zero-filling | 12 entries, zeros present |
| RPT-xx | Instructor sees only own courses | other courses absent |
| SEC-xx | No token | 401 |
| SEC-xx | Student on admin/instructor routes | 403 |
| SEC-xx | Instructor edits another's course | 403 |

## End-to-end journey

Admin creates category → instructor creates course → publish fails (no lessons) → adds
sections + lessons → publishes → student sees it in catalogue and outline → lesson content 403
→ enrolls → content 200 → completes lessons → continue-learning → takes quiz and passes →
reviews → admin overview and top courses → instructor per-course report and trend →
instructor deletes course → archived.

## Postman collection

Files: `elearning.postman_collection.json` (v2.1) and `elearning-local.postman_environment.json`.

Environment variables: `baseUrl=http://localhost:8080`, `keycloakUrl=http://localhost:8081`,
`realm=elearning`, `tokenClientId=elearning-password-check`, credentials for admin,
instructor, instructor2, student, student2, and empty `adminToken`, `instructorToken`,
`instructor2Token`, `studentToken`, `student2Token`, `categoryId`, `courseId`, `courseSlug`,
`sectionId`, `lessonId`, `lessonId2`, `quizId`, `attemptId`, `reviewId`, `questionId`,
`optionId`.

Folders, run in order:

| Folder | Content |
|---|---|
| 00 Auth | Token per role: `POST {{keycloakUrl}}/realms/{{realm}}/protocol/openid-connect/token` (x-www-form-urlencoded, `grant_type=password`); save `access_token`; then `GET /api/me` per role to provision users |
| 01 Categories | admin CRUD, public list, negatives |
| 02 Courses | create, publish fails 400, search/filter/sort |
| 03 Content | sections, VIDEO + TEXT lessons, reorder, publish OK, outline hides content, student 403 on lesson |
| 04 Enrollment | enroll 201, again 409, content accessible, complete lessons, progress, continue, my courses |
| 05 Quizzes | create quiz, student view has no `correct`, pass attempt, fail attempt |
| 06 Reviews | review 201, second 409, student2 403, avgRating updated |
| 07 Reports | admin overview, top courses, instructor per-course, trend has 12 entries |
| 08 Security | no token 401, student on admin 403, student on instructor 403, instructor2 edits course 403 |
| 09 Cleanup | delete course with enrollments → archived; delete test category |

Per-request rules:
- `Authorization: Bearer {{<role>Token}}` set per request, not collection-wide.
- `pm.test` asserting status + 1–3 key body fields.
- Save created ids with `pm.environment.set` for chaining.
- Unique names via `{{$timestamp}}` so reruns work.
- Request names start with the test-case ID, e.g. `ENR-02 Enroll twice → 409`.

Example test script:

```javascript
pm.test("201 Created", () => pm.response.to.have.status(201));
const body = pm.response.json();
pm.test("has id", () => pm.expect(body.id).to.be.a("number"));
pm.environment.set("courseId", body.id);
```

Run from the terminal:

```bash
npm install -g newman newman-reporter-htmlextra
newman run elearning.postman_collection.json \
  -e elearning-local.postman_environment.json \
  -r cli,htmlextra --reporter-htmlextra-export reports/elearning.html
```

Before a full run: recreate `elearning_db`, restart the app (Flyway rebuilds the schema).
The password grant on `elearning-password-check` is for dev/test only — disable it in
production.
