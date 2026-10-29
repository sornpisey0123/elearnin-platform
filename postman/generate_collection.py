"""Generates elearning.postman_collection.json and elearning-local.postman_environment.json next to this script.

Run: python postman/generate_collection.py
"""
import json
import sys
from pathlib import Path

OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).parent

TOKENS = {"admin": "adminToken", "instructor": "instructorToken", "instructor2": "instructor2Token",
          "student": "studentToken", "student2": "student2Token", "none": None}


def status(code, label=None):
    names = {200: "OK", 201: "Created", 204: "No Content", 400: "Bad Request", 401: "Unauthorized",
             403: "Forbidden", 404: "Not Found", 409: "Conflict"}
    return f'pm.test("{code} {label or names[code]}", () => pm.response.to.have.status({code}));'


def req(name, method, path, role="none", body=None, tests=(), code=200, prerequest=None, raw_url=None):
    headers = []
    if TOKENS[role]:
        headers.append({"key": "Authorization", "value": "Bearer {{" + TOKENS[role] + "}}"})
    request = {"method": method, "header": headers}
    url = raw_url or "{{baseUrl}}" + path
    request["url"] = url
    if body is not None:
        headers.append({"key": "Content-Type", "value": "application/json"})
        request["body"] = {"mode": "raw", "raw": fix_numbers(json.dumps(body, indent=2)),
                           "options": {"raw": {"language": "json"}}}
    exec_lines = [status(code)]
    if code != 204 and tests:
        exec_lines.append("const body = pm.response.json();")
    exec_lines += list(tests)
    events = [{"listen": "test", "script": {"type": "text/javascript", "exec": exec_lines}}]
    if prerequest:
        events.insert(0, {"listen": "prerequest", "script": {"type": "text/javascript", "exec": prerequest}})
    return {"name": name, "event": events, "request": request}


def folder(name, items, description=""):
    return {"name": name, "description": description, "item": items}


def token(role, user_var, pass_var):
    return {
        "name": f"AUTH Token {role}",
        "event": [{"listen": "test", "script": {"type": "text/javascript", "exec": [
            status(200),
            "const body = pm.response.json();",
            'pm.test("has access_token", () => pm.expect(body.access_token).to.be.a("string"));',
            f'pm.environment.set("{TOKENS[role]}", body.access_token);',
        ]}}],
        "request": {
            "method": "POST",
            "header": [{"key": "Content-Type", "value": "application/x-www-form-urlencoded"}],
            "body": {"mode": "urlencoded", "urlencoded": [
                {"key": "grant_type", "value": "password"},
                {"key": "client_id", "value": "{{tokenClientId}}"},
                {"key": "username", "value": "{{" + user_var + "}}"},
                {"key": "password", "value": "{{" + pass_var + "}}"},
                {"key": "scope", "value": "openid email profile"},
            ]},
            "url": "{{keycloakUrl}}/realms/{{realm}}/protocol/openid-connect/token",
        },
    }


def me(role, id_var, expected_role):
    return req(f"AUTH GET /api/me as {role} (provisions user)", "GET", "/api/me", role, tests=[
        f'pm.test("role is {expected_role}", () => pm.expect(body.role).to.eql("{expected_role}"));',
        f'pm.environment.set("{id_var}", body.id);',
    ])


course_body = {"title": "Postman Course {{runId}}", "description": "Created by the Postman collection",
               "categoryId": "{{categoryId}}", "level": "BEGINNER", "price": 49.99}


def course(title):
    # categoryId must be a number, so the placeholder is unquoted after dumping
    return {**course_body, "title": title}


def fix_numbers(text):
    for var in ("categoryId", "courseId", "sectionId", "sectionId2", "lessonId", "lessonId2", "lessonId3"):
        text = text.replace(f'"{{{{{var}}}}}"', f"{{{{{var}}}}}")
    return text


# ---------------------------------------------------------------- 00 Auth
auth = folder("00 Auth", [
    {
        "name": "AUTH Init run id",
        "event": [
            {"listen": "prerequest", "script": {"type": "text/javascript", "exec": [
                "// One id per run so names stay unique across reruns",
                'pm.environment.set("runId", String(Date.now()));',
            ]}},
            {"listen": "test", "script": {"type": "text/javascript", "exec": [
                'pm.test("Keycloak realm reachable", () => pm.response.to.have.status(200));',
            ]}},
        ],
        "request": {"method": "GET", "header": [], "url": "{{keycloakUrl}}/realms/{{realm}}"},
    },
    token("admin", "adminUsername", "adminPassword"),
    token("instructor", "instructorUsername", "instructorPassword"),
    token("instructor2", "instructor2Username", "instructor2Password"),
    token("student", "studentUsername", "studentPassword"),
    token("student2", "student2Username", "student2Password"),
    me("admin", "adminUserId", "ADMIN"),
    me("instructor", "instructorUserId", "INSTRUCTOR"),
    me("instructor2", "instructor2UserId", "INSTRUCTOR"),
    me("student", "studentUserId", "STUDENT"),
    me("student2", "student2UserId", "STUDENT"),
    req("SEC-02 Debug token shows roles", "GET", "/api/me/debug-token", "student", tests=[
        'pm.test("contains STUDENT", () => pm.expect(JSON.stringify(body)).to.include("STUDENT"));',
    ]),
], "Password-grant tokens for every role, then /api/me to provision the local users.")

# ---------------------------------------------------------------- 01 Users
users = folder("01 Users", [
    req("USR-01 Get my profile", "GET", "/api/me", "student", tests=[
        'pm.test("has email", () => pm.expect(body.email).to.be.a("string"));',
        'pm.environment.set("studentFullName", body.fullName);',
    ]),
    req("USR-02 Update profile (same name) → 200", "PUT", "/api/me", "student",
        body={"fullName": "{{studentFullName}}", "bio": "Bio set by Postman {{runId}}"}, tests=[
            'pm.test("bio updated", () => pm.expect(body.bio).to.eql("Bio set by Postman " + pm.environment.get("runId")));',
        ]),
    req("USR-03 Update profile blank fullName → 400", "PUT", "/api/me", "student",
        body={"fullName": "", "bio": "x"}, code=400, tests=[
            'pm.test("fullName error", () => pm.expect(body.errors).to.have.property("fullName"));',
        ]),
    req("USR-04 Update profile invalid email → 400", "PUT", "/api/me", "student",
        body={"fullName": "Someone", "email": "not-an-email"}, code=400, tests=[
            'pm.test("email error", () => pm.expect(body.errors).to.have.property("email"));',
        ]),
    req("USR-05 Change password, confirmation mismatch → 400", "PUT", "/api/me/password", "student",
        body={"currentPassword": "whatever", "newPassword": "NewPass@123", "confirmPassword": "Other@123"},
        code=400),
    req("USR-06 Admin lists users", "GET", "/api/admin/users?page=0&size=50", "admin", tests=[
        'pm.test("paged", () => pm.expect(body.content).to.be.an("array"));',
        'pm.test("totalElements >= 5", () => pm.expect(body.totalElements).to.be.at.least(5));',
    ]),
    req("USR-07 Admin filters users by role", "GET", "/api/admin/users?role=INSTRUCTOR", "admin", tests=[
        'pm.test("only instructors", () => body.content.forEach(u => pm.expect(u.role).to.eql("INSTRUCTOR")));',
    ]),
    req("USR-08 Admin changes own role → 403", "PATCH", "/api/admin/users/{{adminUserId}}/role", "admin",
        body={"role": "STUDENT"}, code=403),
    req("USR-09 Admin changes own status → 403", "PATCH", "/api/admin/users/{{adminUserId}}/status", "admin",
        body={"active": False}, code=403),
    req("USR-10 Admin sets same role (no-op) → 200", "PATCH", "/api/admin/users/{{student2UserId}}/role", "admin",
        body={"role": "STUDENT"}, tests=[
            'pm.test("still STUDENT", () => pm.expect(body.role).to.eql("STUDENT"));',
        ]),
    req("USR-11 Change role, missing role → 400", "PATCH", "/api/admin/users/{{student2UserId}}/role", "admin",
        body={}, code=400),
    req("USR-12 Change role, unknown user → 404", "PATCH", "/api/admin/users/999999999/role", "admin",
        body={"role": "STUDENT"}, code=404),
    req("USR-13 Student lists users → 403", "GET", "/api/admin/users", "student", code=403),
    req("USR-14 No token on /api/me → 401", "GET", "/api/me", code=401),
])

# ---------------------------------------------------------------- 02 Categories
categories = folder("02 Categories", [
    req("CAT-01 Public list categories", "GET", "/api/categories", tests=[
        'pm.test("seeded categories present", () => pm.expect(body.map(c => c.slug)).to.include.members(["programming", "design", "business"]));',
    ]),
    req("CAT-02 Admin creates category → 201", "POST", "/api/admin/categories", "admin",
        body={"name": "Postman Cat {{runId}}", "description": "Test category"}, code=201, tests=[
            'pm.test("has id", () => pm.expect(body.id).to.be.a("number"));',
            'pm.test("slug generated", () => pm.expect(body.slug).to.eql("postman-cat-" + pm.environment.get("runId")));',
            'pm.test("courseCount 0", () => pm.expect(body.courseCount).to.eql(0));',
            'pm.environment.set("categoryId", body.id);',
            'pm.environment.set("categorySlug", body.slug);',
        ]),
    req("CAT-03 Duplicate name → 409", "POST", "/api/admin/categories", "admin",
        body={"name": "Postman Cat {{runId}}"}, code=409),
    req("CAT-04 Blank name → 400", "POST", "/api/admin/categories", "admin", body={"name": ""}, code=400, tests=[
        'pm.test("name error", () => pm.expect(body.errors).to.have.property("name"));',
    ]),
    req("CAT-05 Name without letters or digits → 400", "POST", "/api/admin/categories", "admin",
        body={"name": "!!!"}, code=400),
    req("CAT-06 Student creates category → 403", "POST", "/api/admin/categories", "student",
        body={"name": "Nope {{runId}}"}, code=403),
    req("CAT-07 Instructor creates category → 403", "POST", "/api/admin/categories", "instructor",
        body={"name": "Nope {{runId}}"}, code=403),
    req("CAT-08 No token creates category → 401", "POST", "/api/admin/categories",
        body={"name": "Nope {{runId}}"}, code=401),
    req("CAT-09 Admin updates category", "PUT", "/api/admin/categories/{{categoryId}}", "admin",
        body={"name": "Postman Cat {{runId}}", "description": "Updated description"}, tests=[
            'pm.test("description updated", () => pm.expect(body.description).to.eql("Updated description"));',
        ]),
    req("CAT-10 Update unknown category → 404", "PUT", "/api/admin/categories/999999999", "admin",
        body={"name": "Ghost {{runId}}"}, code=404),
    req("CAT-11 Create throwaway category", "POST", "/api/admin/categories", "admin",
        body={"name": "Postman Temp {{runId}}"}, code=201, tests=[
            'pm.environment.set("tempCategoryId", body.id);',
        ]),
    req("CAT-12 Delete unused category → 204", "DELETE", "/api/admin/categories/{{tempCategoryId}}", "admin",
        code=204),
    req("CAT-13 Delete unknown category → 404", "DELETE", "/api/admin/categories/{{tempCategoryId}}", "admin",
        code=404),
    req("CAT-14 Courses of unknown category slug → 404", "GET", "/api/categories/no-such-slug-{{runId}}/courses",
        code=404),
])

# ---------------------------------------------------------------- 03 Courses
courses = folder("03 Courses", [
    req("CRS-01 Instructor creates course → 201 DRAFT", "POST", "/api/instructor/courses", "instructor",
        body=course("Postman Course {{runId}}"), code=201, tests=[
            'pm.test("status DRAFT", () => pm.expect(body.status).to.eql("DRAFT"));',
            'pm.test("instructor is caller", () => pm.expect(body.instructor.id).to.eql(Number(pm.environment.get("instructorUserId"))));',
            'pm.environment.set("courseId", body.id);',
            'pm.environment.set("courseSlug", body.slug);',
        ]),
    req("CRS-02 Instructor creates second (draft) course", "POST", "/api/instructor/courses", "instructor",
        body=course("Postman Draft {{runId}}"), code=201, tests=[
            'pm.environment.set("draftCourseId", body.id);',
            'pm.environment.set("draftCourseSlug", body.slug);',
        ]),
    req("CRS-03 Missing fields → 400", "POST", "/api/instructor/courses", "instructor",
        body={"title": "", "price": -1}, code=400, tests=[
            'pm.test("field errors", () => pm.expect(body.errors).to.include.keys("title", "categoryId", "level", "price"));',
        ]),
    req("CRS-04 Unknown category → 400", "POST", "/api/instructor/courses", "instructor",
        body={**course("Ghost {{runId}}"), "categoryId": 999999999}, code=400),
    req("CRS-05 Student creates course → 403", "POST", "/api/instructor/courses", "student",
        body=course("Nope {{runId}}"), code=403),
    req("CRS-06 Publish without sections → 400", "POST", "/api/instructor/courses/{{courseId}}/publish",
        "instructor", code=400, tests=[
            'pm.test("mentions section", () => pm.expect(body.detail).to.include("section"));',
        ]),
    req("CRS-07 Instructor updates own course", "PUT", "/api/instructor/courses/{{courseId}}", "instructor",
        body={**course("Postman Course {{runId}}"), "level": "INTERMEDIATE", "price": 59.99}, tests=[
            'pm.test("level updated", () => pm.expect(body.level).to.eql("INTERMEDIATE"));',
            'pm.test("price updated", () => pm.expect(Number(body.price)).to.eql(59.99));',
        ]),
    req("CRS-08 Admin edits any course (ownership bypass) → 200", "PUT", "/api/instructor/courses/{{courseId}}",
        "admin", body={**course("Postman Course {{runId}}"), "level": "INTERMEDIATE", "price": 59.99}, tests=[
            'pm.test("same id", () => pm.expect(body.id).to.eql(Number(pm.environment.get("courseId"))));',
        ]),
    req("CRS-09 Update unknown course → 404", "PUT", "/api/instructor/courses/999999999", "instructor",
        body=course("Ghost {{runId}}"), code=404),
    req("CRS-10 Instructor lists own courses", "GET", "/api/instructor/courses?size=100", "instructor", tests=[
        'const ids = body.content.map(c => c.id);',
        'pm.test("contains both courses", () => pm.expect(ids).to.include.members([Number(pm.environment.get("courseId")), Number(pm.environment.get("draftCourseId"))]));',
    ]),
    req("CRS-11 instructor2 does not see them", "GET", "/api/instructor/courses?size=100", "instructor2", tests=[
        'pm.test("course absent", () => pm.expect(body.content.map(c => c.id)).to.not.include(Number(pm.environment.get("courseId"))));',
    ]),
    req("CRS-12 Draft course detail is hidden from public → 404", "GET", "/api/courses/{{courseSlug}}", code=404),
    req("CRS-13 Draft course detail visible to owner", "GET", "/api/courses/{{courseSlug}}", "instructor", tests=[
        'pm.test("status DRAFT", () => pm.expect(body.status).to.eql("DRAFT"));',
    ]),
    req("CRS-14 Unknown sort → 400", "GET", "/api/courses?sort=cheapest", code=400),
    req("CRS-15 minPrice > maxPrice → 400", "GET", "/api/courses?minPrice=100&maxPrice=10", code=400),
])

# ---------------------------------------------------------------- 04 Content
content = folder("04 Content", [
    req("CNT-01 Create section 1 → 201", "POST", "/api/instructor/courses/{{courseId}}/sections", "instructor",
        body={"title": "Getting started"}, code=201, tests=[
            'pm.test("sortOrder 1", () => pm.expect(body.sortOrder).to.eql(1));',
            'pm.environment.set("sectionId", body.id);',
        ]),
    req("CNT-02 Create section 2 → 201", "POST", "/api/instructor/courses/{{courseId}}/sections", "instructor",
        body={"title": "Going further"}, code=201, tests=[
            'pm.test("appended", () => pm.expect(body.sortOrder).to.eql(2));',
            'pm.environment.set("sectionId2", body.id);',
        ]),
    req("CNT-03 Publish with sections but no lessons → 400", "POST",
        "/api/instructor/courses/{{courseId}}/publish", "instructor", code=400, tests=[
            'pm.test("mentions lesson", () => pm.expect(body.detail).to.include("lesson"));',
        ]),
    req("CNT-04 Blank section title → 400", "POST", "/api/instructor/courses/{{courseId}}/sections", "instructor",
        body={"title": ""}, code=400),
    req("CNT-05 instructor2 adds section to another's course → 403", "POST",
        "/api/instructor/courses/{{courseId}}/sections", "instructor2", body={"title": "Hijack"}, code=403),
    req("CNT-06 Rename section", "PUT", "/api/instructor/sections/{{sectionId}}", "instructor",
        body={"title": "Introduction"}, tests=[
            'pm.test("renamed", () => pm.expect(body.title).to.eql("Introduction"));',
        ]),
    req("CNT-07 Create VIDEO lesson → 201", "POST", "/api/instructor/sections/{{sectionId}}/lessons", "instructor",
        body={"title": "Welcome video", "type": "VIDEO", "videoUrl": "https://example.com/welcome.mp4",
              "durationSeconds": 300, "preview": False}, code=201, tests=[
            'pm.test("type VIDEO", () => pm.expect(body.type).to.eql("VIDEO"));',
            'pm.environment.set("lessonId", body.id);',
        ]),
    req("CNT-08 Create TEXT preview lesson → 201", "POST", "/api/instructor/sections/{{sectionId}}/lessons",
        "instructor", body={"title": "Course notes", "type": "TEXT", "body": "# Notes\nRead me first.",
                            "preview": True}, code=201, tests=[
            'pm.test("preview true", () => pm.expect(body.preview).to.eql(true));',
            'pm.environment.set("lessonId2", body.id);',
        ]),
    req("CNT-09 Create TEXT lesson in section 2 → 201", "POST", "/api/instructor/sections/{{sectionId2}}/lessons",
        "instructor", body={"title": "Deep dive", "type": "TEXT", "body": "More content."}, code=201, tests=[
            'pm.environment.set("lessonId3", body.id);',
        ]),
    req("CNT-10 VIDEO lesson without videoUrl → 400", "POST", "/api/instructor/sections/{{sectionId}}/lessons",
        "instructor", body={"title": "Broken", "type": "VIDEO"}, code=400),
    req("CNT-11 TEXT lesson without body → 400", "POST", "/api/instructor/sections/{{sectionId}}/lessons",
        "instructor", body={"title": "Broken", "type": "TEXT"}, code=400),
    req("CNT-12 Invalid videoUrl → 400", "POST", "/api/instructor/sections/{{sectionId}}/lessons", "instructor",
        body={"title": "Broken", "type": "VIDEO", "videoUrl": "ftp://nope", "durationSeconds": 10}, code=400),
    req("CNT-13 Lesson in unknown section → 404", "POST", "/api/instructor/sections/999999999/lessons",
        "instructor", body={"title": "Ghost", "type": "TEXT", "body": "x"}, code=404),
    req("CNT-14 Update lesson", "PUT", "/api/instructor/lessons/{{lessonId}}", "instructor",
        body={"title": "Welcome video (updated)", "type": "VIDEO", "videoUrl": "https://example.com/welcome.mp4",
              "durationSeconds": 360, "preview": False}, tests=[
            'pm.test("duration updated", () => pm.expect(body.durationSeconds).to.eql(360));',
        ]),
    req("CNT-15 Reorder lessons with wrong id set → 400", "PUT",
        "/api/instructor/sections/{{sectionId}}/lessons/order", "instructor",
        body={"lessonIds": ["{{lessonId}}"]}, code=400),
    req("CNT-16 Reorder lessons → 200", "PUT", "/api/instructor/sections/{{sectionId}}/lessons/order",
        "instructor", body={"lessonIds": ["{{lessonId2}}", "{{lessonId}}"]}, tests=[
            'pm.test("new order", () => pm.expect(body.map(l => l.id)).to.eql([Number(pm.environment.get("lessonId2")), Number(pm.environment.get("lessonId"))]));',
        ]),
    req("CNT-17 Reorder sections with wrong id set → 400", "PUT",
        "/api/instructor/courses/{{courseId}}/sections/order", "instructor",
        body={"sectionIds": ["{{sectionId}}", "{{sectionId}}"]}, code=400),
    req("CNT-18 Reorder sections (same order) → 200", "PUT", "/api/instructor/courses/{{courseId}}/sections/order",
        "instructor", body={"sectionIds": ["{{sectionId}}", "{{sectionId2}}"]}, tests=[
            'pm.test("order kept", () => pm.expect(body.map(s => s.id)).to.eql([Number(pm.environment.get("sectionId")), Number(pm.environment.get("sectionId2"))]));',
        ]),
    req("CNT-19 Create and delete a section → 201", "POST", "/api/instructor/courses/{{courseId}}/sections",
        "instructor", body={"title": "To be deleted"}, code=201, tests=[
            'pm.environment.set("tempSectionId", body.id);',
        ]),
    req("CNT-20 Delete section → 204", "DELETE", "/api/instructor/sections/{{tempSectionId}}", "instructor",
        code=204),
    req("CNT-21 Publish → 200 PUBLISHED", "POST", "/api/instructor/courses/{{courseId}}/publish", "instructor",
        tests=[
            'pm.test("status PUBLISHED", () => pm.expect(body.status).to.eql("PUBLISHED"));',
            'pm.test("publishedAt set", () => pm.expect(body.publishedAt).to.be.a("string"));',
            'pm.test("3 lessons", () => pm.expect(body.lessonCount).to.eql(3));',
            'pm.environment.set("courseSlug", body.slug);',
        ]),
    req("CNT-22 Public outline hides lesson content", "GET", "/api/courses/{{courseId}}/outline", tests=[
        'pm.test("2 sections", () => pm.expect(body.sections.length).to.eql(2));',
        'const lessons = body.sections.flatMap(s => s.lessons);',
        'pm.test("3 lessons", () => pm.expect(lessons.length).to.eql(3));',
        'pm.test("no content fields", () => lessons.forEach(l => pm.expect(l).to.not.have.any.keys("videoUrl", "body", "fileUrl")));',
    ]),
    req("CNT-23 Student opens non-preview lesson, not enrolled → 403", "GET", "/api/lessons/{{lessonId}}",
        "student", code=403),
    req("CNT-24 Student opens preview lesson, not enrolled → 200", "GET", "/api/lessons/{{lessonId2}}",
        "student", tests=[
            'pm.test("body present", () => pm.expect(body.body).to.include("Notes"));',
        ]),
    req("CNT-25 Lesson without token → 401", "GET", "/api/lessons/{{lessonId}}", code=401),
    req("CNT-26 Unknown lesson → 404", "GET", "/api/lessons/999999999", "student", code=404),
    req("CNT-27 Owner opens any lesson → 200", "GET", "/api/lessons/{{lessonId}}", "instructor", tests=[
        'pm.test("videoUrl present", () => pm.expect(body.videoUrl).to.eql("https://example.com/welcome.mp4"));',
    ]),
])

# ---------------------------------------------------------------- 05 Catalogue
catalogue = folder("05 Catalogue", [
    req("CRS-16 Search by keyword finds published course only", "GET", "/api/courses?keyword={{runId}}&size=50",
        tests=[
            'const ids = body.content.map(c => c.id);',
            'pm.test("published course found", () => pm.expect(ids).to.include(Number(pm.environment.get("courseId"))));',
            'pm.test("draft course absent", () => pm.expect(ids).to.not.include(Number(pm.environment.get("draftCourseId"))));',
        ]),
    req("CRS-17 Filter by category and level", "GET",
        "/api/courses?categoryId={{categoryId}}&level=INTERMEDIATE", tests=[
            'pm.test("exactly the course", () => pm.expect(body.content.map(c => c.id)).to.eql([Number(pm.environment.get("courseId"))]));',
        ]),
    req("CRS-18 Filter by price range excludes it", "GET",
        "/api/courses?categoryId={{categoryId}}&minPrice=0&maxPrice=10", tests=[
            'pm.test("empty", () => pm.expect(body.totalElements).to.eql(0));',
        ]),
    req("CRS-19 Sort newest", "GET", "/api/courses?sort=newest&size=50", tests=[
        'pm.test("paged response", () => pm.expect(body).to.include.keys("content", "page", "size", "totalElements", "totalPages"));',
    ]),
    req("CRS-20 Sort popular is ordered by enrollmentCount desc", "GET", "/api/courses?sort=popular&size=50",
        tests=[
            'const counts = body.content.map(c => c.enrollmentCount);',
            'pm.test("descending", () => pm.expect(counts).to.eql([...counts].sort((a, b) => b - a)));',
        ]),
    req("CRS-21 Sort rating is ordered by avgRating desc", "GET", "/api/courses?sort=rating&size=50", tests=[
        'const ratings = body.content.map(c => Number(c.avgRating || 0));',
        'pm.test("descending", () => pm.expect(ratings).to.eql([...ratings].sort((a, b) => b - a)));',
    ]),
    req("CRS-22 Course detail by slug (public)", "GET", "/api/courses/{{courseSlug}}", tests=[
        'pm.test("published", () => pm.expect(body.status).to.eql("PUBLISHED"));',
        'pm.test("category", () => pm.expect(body.category.id).to.eql(Number(pm.environment.get("categoryId"))));',
        'pm.test("2 sections", () => pm.expect(body.sectionCount).to.eql(2));',
    ]),
    req("CRS-23 Unknown slug → 404", "GET", "/api/courses/no-such-course-{{runId}}", code=404),
    req("CAT-15 courseCount ignores drafts", "GET", "/api/categories", tests=[
        'const cat = body.find(c => c.id === Number(pm.environment.get("categoryId")));',
        'pm.test("1 published course (draft excluded)", () => pm.expect(cat.courseCount).to.eql(1));',
    ]),
    req("CAT-16 Published courses of the category", "GET", "/api/categories/{{categorySlug}}/courses", tests=[
        'pm.test("only the published course", () => pm.expect(body.content.map(c => c.id)).to.eql([Number(pm.environment.get("courseId"))]));',
    ]),
])

# ---------------------------------------------------------------- 06 Enrollment
enrollment = folder("06 Enrollment & Progress", [
    req("ENR-01 Student enrolls → 201", "POST", "/api/courses/{{courseId}}/enroll", "student", code=201, tests=[
        'pm.test("ACTIVE", () => pm.expect(body.status).to.eql("ACTIVE"));',
        'pm.test("pricePaid = current price", () => pm.expect(Number(body.pricePaid)).to.eql(59.99));',
        'pm.test("progress 0", () => pm.expect(Number(body.progressPct)).to.eql(0));',
        'pm.environment.set("enrollmentId", body.id);',
    ]),
    req("ENR-02 Enroll twice → 409", "POST", "/api/courses/{{courseId}}/enroll", "student", code=409),
    req("ENR-03 Enroll in draft course → 400", "POST", "/api/courses/{{draftCourseId}}/enroll", "student",
        code=400),
    req("ENR-04 Instructor enrolls in own course → 400", "POST", "/api/courses/{{courseId}}/enroll", "instructor",
        code=400),
    req("ENR-05 Enroll in unknown course → 404", "POST", "/api/courses/999999999/enroll", "student", code=404),
    req("ENR-06 Enroll without token → 401", "POST", "/api/courses/{{courseId}}/enroll", code=401),
    req("ENR-07 Enrolled student opens lesson → 200", "GET", "/api/lessons/{{lessonId}}", "student", tests=[
        'pm.test("videoUrl present", () => pm.expect(body.videoUrl).to.be.a("string"));',
    ]),
    req("ENR-08 Continue learning → first lesson in outline order", "GET",
        "/api/me/courses/{{courseId}}/continue", "student", tests=[
            'pm.test("not completed", () => pm.expect(body.courseCompleted).to.eql(false));',
            'pm.test("first lesson after reorder", () => pm.expect(body.lesson.id).to.eql(Number(pm.environment.get("lessonId2"))));',
        ]),
    req("ENR-09 Complete lesson 1 of 3", "POST", "/api/me/lessons/{{lessonId2}}/complete", "student", tests=[
        'pm.test("lesson completed", () => pm.expect(body.lessonCompleted).to.eql(true));',
        'pm.test("1 of 3", () => { pm.expect(body.completedLessons).to.eql(1); pm.expect(body.totalLessons).to.eql(3); });',
        'pm.test("still ACTIVE", () => pm.expect(body.enrollmentStatus).to.eql("ACTIVE"));',
    ]),
    req("ENR-10 Complete same lesson again is idempotent", "POST", "/api/me/lessons/{{lessonId2}}/complete",
        "student", tests=[
            'pm.test("still 1", () => pm.expect(body.completedLessons).to.eql(1));',
        ]),
    req("ENR-11 Continue learning → next incomplete lesson", "GET", "/api/me/courses/{{courseId}}/continue",
        "student", tests=[
            'pm.test("second lesson", () => pm.expect(body.lesson.id).to.eql(Number(pm.environment.get("lessonId"))));',
        ]),
    req("ENR-12 Complete lesson 2 of 3", "POST", "/api/me/lessons/{{lessonId}}/complete", "student", tests=[
        'pm.test("2 of 3", () => pm.expect(body.completedLessons).to.eql(2));',
    ]),
    req("ENR-13 Complete last lesson → COMPLETED, 100%", "POST", "/api/me/lessons/{{lessonId3}}/complete",
        "student", tests=[
            'pm.test("COMPLETED", () => pm.expect(body.enrollmentStatus).to.eql("COMPLETED"));',
            'pm.test("100%", () => pm.expect(Number(body.progressPct)).to.eql(100));',
        ]),
    req("ENR-14 Continue learning when completed → first lesson", "GET",
        "/api/me/courses/{{courseId}}/continue", "student", tests=[
            'pm.test("courseCompleted", () => pm.expect(body.courseCompleted).to.eql(true));',
            'pm.test("points to start", () => pm.expect(body.lesson.id).to.eql(Number(pm.environment.get("lessonId2"))));',
        ]),
    req("ENR-15 Un-complete a lesson → back to ACTIVE", "DELETE", "/api/me/lessons/{{lessonId3}}/complete",
        "student", tests=[
            'pm.test("lesson not completed", () => pm.expect(body.lessonCompleted).to.eql(false));',
            'pm.test("ACTIVE", () => pm.expect(body.enrollmentStatus).to.eql("ACTIVE"));',
        ]),
    req("ENR-16 Complete it again → COMPLETED", "POST", "/api/me/lessons/{{lessonId3}}/complete", "student",
        tests=[
            'pm.test("COMPLETED", () => pm.expect(body.enrollmentStatus).to.eql("COMPLETED"));',
        ]),
    req("ENR-17 Complete lesson, not enrolled → 403", "POST", "/api/me/lessons/{{lessonId}}/complete",
        "student2", code=403),
    req("ENR-18 Complete unknown lesson → 404", "POST", "/api/me/lessons/999999999/complete", "student",
        code=404),
    req("ENR-19 Continue learning, not enrolled → 403", "GET", "/api/me/courses/{{courseId}}/continue",
        "student2", code=403),
    req("ENR-20 My courses lists the enrollment", "GET", "/api/me/courses", "student", tests=[
        'const row = body.content.find(r => r.course.id === Number(pm.environment.get("courseId")));',
        'pm.test("enrollment listed", () => pm.expect(row).to.be.an("object"));',
        'pm.test("COMPLETED 100", () => { pm.expect(row.status).to.eql("COMPLETED"); pm.expect(Number(row.progressPct)).to.eql(100); });',
    ]),
    req("ENR-21 My courses ?status=completed", "GET", "/api/me/courses?status=completed", "student", tests=[
        'pm.test("all completed", () => body.content.forEach(r => pm.expect(r.status).to.eql("COMPLETED")));',
    ]),
    req("ENR-22 My courses ?status=in-progress excludes it", "GET", "/api/me/courses?status=in-progress",
        "student", tests=[
            'pm.test("course absent", () => pm.expect(body.content.map(r => r.course.id)).to.not.include(Number(pm.environment.get("courseId"))));',
        ]),
    req("ENR-23 My courses unknown status → 400", "GET", "/api/me/courses?status=bogus", "student", code=400),
    req("ENR-24 student2 enrolls → 201", "POST", "/api/courses/{{courseId}}/enroll", "student2", code=201),
    req("ENR-25 Enrollment count increased", "GET", "/api/courses/{{courseSlug}}", tests=[
        'pm.test("2 enrollments", () => pm.expect(body.enrollmentCount).to.eql(2));',
    ]),
    req("CRS-24 Unpublish with active enrollments → 409", "POST",
        "/api/instructor/courses/{{courseId}}/unpublish", "instructor", code=409),
])

# ---------------------------------------------------------------- 07 Security
security = folder("07 Security", [
    req("SEC-01 No token on instructor route → 401", "GET", "/api/instructor/courses", code=401),
    req("SEC-03 Student on admin route → 403", "GET", "/api/admin/users", "student", code=403),
    req("SEC-04 Student on instructor route → 403", "GET", "/api/instructor/courses", "student", code=403),
    req("SEC-05 Instructor on admin route → 403", "PUT", "/api/admin/categories/{{categoryId}}", "instructor",
        body={"name": "Hijack"}, code=403),
    req("SEC-06 instructor2 edits another's course → 403", "PUT", "/api/instructor/courses/{{courseId}}",
        "instructor2", body=course("Hijacked {{runId}}"), code=403),
    req("SEC-07 instructor2 publishes another's course → 403", "POST",
        "/api/instructor/courses/{{draftCourseId}}/publish", "instructor2", code=403),
    req("SEC-08 instructor2 edits another's lesson → 403", "PUT", "/api/instructor/lessons/{{lessonId}}",
        "instructor2", body={"title": "Hijacked", "type": "TEXT", "body": "x"}, code=403),
    req("SEC-09 instructor2 deletes another's section → 403", "DELETE", "/api/instructor/sections/{{sectionId2}}",
        "instructor2", code=403),
    req("SEC-10 instructor2 deletes another's course → 403", "DELETE", "/api/instructor/courses/{{courseId}}",
        "instructor2", code=403),
    req("SEC-11 Invalid token → 401", "GET", "/api/me", code=401, prerequest=[
        'pm.request.headers.upsert({key: "Authorization", value: "Bearer not-a-real-token"});',
    ]),
])

# ---------------------------------------------------------------- 08 Cleanup
cleanup = folder("08 Cleanup", [
    req("CRS-25 Delete course without enrollments → 204", "DELETE", "/api/instructor/courses/{{draftCourseId}}",
        "instructor", code=204),
    req("CRS-26 Deleted course is gone → 404", "GET", "/api/courses/{{draftCourseSlug}}", "instructor", code=404),
    req("CRS-27 Delete course with enrollments → 200 archived", "DELETE", "/api/instructor/courses/{{courseId}}",
        "instructor", tests=[
            'pm.test("archived true", () => pm.expect(body.archived).to.eql(true));',
        ]),
    req("CRS-28 Archived course hidden from public → 404", "GET", "/api/courses/{{courseSlug}}", code=404),
    req("CRS-29 Archived course visible to enrolled student", "GET", "/api/courses/{{courseSlug}}", "student",
        tests=[
            'pm.test("ARCHIVED", () => pm.expect(body.status).to.eql("ARCHIVED"));',
        ]),
    req("CRS-30 Enrolled student keeps lesson access", "GET", "/api/lessons/{{lessonId}}", "student"),
    req("CRS-31 Archived course not enrollable → 400", "POST", "/api/courses/{{courseId}}/enroll", "student2",
        code=400),
    req("CAT-17 Delete category still used by a course → 409", "DELETE", "/api/admin/categories/{{categoryId}}",
        "admin", code=409),
])

collection = {
    "info": {
        "name": "E-Learning API",
        "description": "End-to-end test run for the e-learning backend (Phases 1–6). Run folders in order on a "
                       "fresh database with Keycloak up. Tokens come from the password grant on "
                       "elearning-password-check (dev/test only).",
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    },
    "item": [auth, users, categories, courses, content, catalogue, enrollment, security, cleanup],
}

env_values = [
    ("baseUrl", "http://localhost:8989", "default"),
    ("keycloakUrl", "http://localhost:8081", "default"),
    ("realm", "elearning", "default"),
    ("tokenClientId", "elearning-password-check", "default"),
    ("adminUsername", "admin", "default"), ("adminPassword", "Admin@123", "secret"),
    ("instructorUsername", "instructor1", "default"), ("instructorPassword", "Instructor@123", "secret"),
    ("instructor2Username", "instructor2", "default"), ("instructor2Password", "Instructor@123", "secret"),
    ("studentUsername", "student1", "default"), ("studentPassword", "Student@123", "secret"),
    ("student2Username", "student2", "default"), ("student2Password", "Student@123", "secret"),
]
runtime = ["runId", "adminToken", "instructorToken", "instructor2Token", "studentToken", "student2Token",
           "adminUserId", "instructorUserId", "instructor2UserId", "studentUserId", "student2UserId",
           "studentFullName", "categoryId", "categorySlug", "tempCategoryId", "courseId", "courseSlug",
           "draftCourseId", "draftCourseSlug", "sectionId", "sectionId2", "tempSectionId", "lessonId", "lessonId2",
           "lessonId3", "enrollmentId"]
environment = {
    "name": "E-Learning Local",
    "values": [{"key": k, "value": v, "type": t, "enabled": True} for k, v, t in env_values]
              + [{"key": k, "value": "", "type": "secret" if k.endswith("Token") else "default", "enabled": True}
                 for k in runtime],
    "_postman_variable_scope": "environment",
}

OUT.mkdir(parents=True, exist_ok=True)
(OUT / "elearning.postman_collection.json").write_text(
    fix_numbers(json.dumps(collection, indent=2, ensure_ascii=False)) + "\n", encoding="utf-8")
(OUT / "elearning-local.postman_environment.json").write_text(
    json.dumps(environment, indent=2) + "\n", encoding="utf-8")
count = sum(len(f["item"]) for f in collection["item"])
print(f"{count} requests written to {OUT}")
