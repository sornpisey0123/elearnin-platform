-- Reference data. Users are not seeded: they are provisioned from Keycloak on first request.

insert into categories (name, slug, description, created_at, updated_at)
values ('Programming', 'programming', 'Software development, languages and frameworks', now(), now()),
       ('Design', 'design', 'UI/UX, graphic design and visual tools', now(), now()),
       ('Business', 'business', 'Management, marketing and entrepreneurship', now(), now());
