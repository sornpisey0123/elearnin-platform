# Keycloak Setup and Security

## Docker (dev)

```yaml
services:
  keycloak:
    image: quay.io/keycloak/keycloak:26.5
    command: start-dev --import-realm
    environment:
      KC_BOOTSTRAP_ADMIN_USERNAME: admin
      KC_BOOTSTRAP_ADMIN_PASSWORD: admin
    ports:
      - "8081:8080"   # Spring Boot uses 8080
    volumes:
      - ./keycloak:/opt/keycloak/data/import
```

Dev mode uses an embedded DB. Production: Keycloak gets its own PostgreSQL database,
separate from `elearning_db`.

## Realm `elearning`

1. Realm roles `ADMIN`, `INSTRUCTOR`, `STUDENT` (uppercase, matching the `Role` enum).
2. `STUDENT` added to default roles; user registration enabled.
3. Clients:

| Client | Type | Settings | Purpose |
|---|---|---|---|
| `elearning-frontend` | public | Standard flow, PKCE S256, redirect URIs `http://localhost:3000/*` and `http://localhost:8080/swagger-ui/oauth2-redirect.html`, web origins `+` | Frontend + Swagger login |
| `elearning-backend` | confidential | Service account only; roles `manage-users`, `view-users`, `view-realm` from `realm-management` | Admin API calls |
| `elearning-password-check` | public | Direct access grants only | Verify current password; Postman tokens (dev only) |

4. Test users: one admin, two instructors, two students.
5. Export the realm (partial export with clients and roles) into `./keycloak/`.

## SecurityConfig

Stateless, CSRF off, CORS for `http://localhost:3000`, `@EnableMethodSecurity`.
Public: swagger, `/v3/api-docs/**`, `/files/**`, GET `/api/categories/**`, `/api/courses`,
`/api/courses/*`, `/api/courses/*/outline`, `/api/courses/*/reviews`.
`/api/admin/**` → ADMIN, `/api/instructor/**` → INSTRUCTOR, all else authenticated.
Keep public GET rules narrow — lesson content lives at `/api/lessons/{id}` (authenticated).

Role mapping — Keycloak puts roles in `realm_access.roles`, which Spring doesn't read by default:

```java
private JwtAuthenticationConverter keycloakJwtConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(jwt -> {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
    });
    return converter;
}
```

## CurrentUserService

`getOrCreate(Jwt)`: find by `keycloakId = UUID(jwt.sub)`; if missing, create from `email`,
`name` (or `given_name` + `family_name`) and realm roles (priority ADMIN > INSTRUCTOR >
STUDENT); if present, sync role/email/name. Controllers use `@AuthenticationPrincipal Jwt jwt`.

## KeycloakAdminService

`RestClient` + `OAuth2ClientHttpRequestInterceptor` with registration `keycloak-admin`
(handles and caches the client-credentials token). Operations:

| Operation | Admin REST call |
|---|---|
| Update name/email | `PUT /admin/realms/elearning/users/{id}` |
| Reset password | `PUT /admin/realms/elearning/users/{id}/reset-password` `{type:"password", value, temporary:false}` |
| Add/remove realm role | `POST`/`DELETE /admin/realms/elearning/users/{id}/role-mappings/realm` (body: role representations from `GET /roles/{name}`) |
| Enable/disable | `PUT /admin/realms/elearning/users/{id}` `{enabled}` |
| Verify password | `POST /realms/elearning/protocol/openid-connect/token` grant `password`, client `elearning-password-check` |

## Password change

Preferred: frontend redirects to Keycloak with `kc_action=UPDATE_PASSWORD` (API never sees
passwords). If the spec requires an API endpoint: `PUT /api/me/password` verifies the current
password via the password grant, then calls reset-password. The password grant is a legacy
flow — dev/test or spec-mandated use only.

## Swagger UI

`OpenApiConfig` defines an OAuth2 authorization-code scheme with
auth URL `http://localhost:8081/realms/elearning/protocol/openid-connect/auth` and
token URL `.../protocol/openid-connect/token`, applied globally.

## Troubleshooting

- **401 on every request:** `issuer-uri` must match the token's `iss` exactly
  (`localhost` vs `127.0.0.1`, port, realm name).
- **403 though the user has the role:** roles not mapped — check the converter and that the
  role is a realm role, not a client role.
- **Admin API 403:** service account missing `realm-management` roles.
- **First check after setup:** temporary `GET /api/me/debug-token` returning `jwt.getClaims()`.
