# Spring Profiles and Runtime Stack

This project uses Spring profiles to separate local development, Docker-based runtime, production, and tests.

## Profile Summary

| Profile | File | Use case | Database target | Secrets |
|---|---|---|---|---|
| `dev` | `application-dev.yaml` | Normal local development from the IDE or `bootRun` | `localhost:16801` by default | Has safe local defaults |
| `stack` | `application-stack.yaml` | Running the backend inside the Docker network with Compose services | `postgres:5432` by default | Has safe stack defaults |
| `prod` | `application-prod.yaml` | Real deployment | Must be provided by environment variables | No secret defaults |
| `test` | `application-test.yaml` | Automated tests | In-memory H2 database | Test-only defaults |

The default profile is `dev`, configured in `application.yaml`:

```yaml
spring:
  profiles:
    default: dev
```

If no profile is selected, Spring Boot starts with `dev`.

## Base Configuration

`src/main/resources/application.yaml` contains shared configuration used by every profile:

- Application name: `campuslink-kh`
- Server port: `8888`
- JPA open-in-view disabled
- Hibernate schema mode: `validate`
- Liquibase enabled with `db/changelog/db.changelog-master.yaml`
- Mail host and port
- JWT issuer, secret, access-token expiry, and refresh-token expiry
- Google OAuth client ID and Firebase project ID
- Default localization language
- Swagger/OpenAPI paths

Profile files only override the values that need to be different per environment.

## Local Development: `dev`

Use `dev` when running the app directly on your machine.

Start PostgreSQL:

```powershell
docker compose -f infrastructure/docker-compose.yml up -d postgres
```

Run the backend:

```powershell
.\gradlew bootRun
```

Because `dev` is the default profile, this is the same as:

```powershell
.\gradlew bootRun --args='--spring.profiles.active=dev'
```

Default `dev` values:

```text
DB_URL=jdbc:postgresql://localhost:16801/campuslink_db
DB_USERNAME=campuslink_usr
DB_PASSWORD=campuslink@168
JWT_ISSUER=campuslink-kh-dev
GOOGLE_PROJECT_ID=genlinkkh-dev
```

Use real mail, Google, or JWT values by setting environment variables before running the app.

## Docker Stack: `stack`

Use `stack` when the backend runs inside the same Docker Compose network as PostgreSQL.

In this profile, the database host is `postgres`:

```text
DB_URL=jdbc:postgresql://postgres:5432/campuslink_db
```

This works from a container because `postgres` is the Compose service name. If you run the backend from your host machine, use `dev` instead, or override `DB_URL`:

```powershell
$env:DB_URL="jdbc:postgresql://localhost:16801/campuslink_db"
.\gradlew bootRun --args='--spring.profiles.active=stack'
```

## Production: `prod`

Use `prod` only for deployed environments.

Production requires real values from environment variables:

```env
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://host:5432/database
DB_USERNAME=production_user
DB_PASSWORD=production_password
JWT_SECRET=replace-with-a-strong-secret-at-least-32-characters
MAIL_USERNAME=real-mail-account
MAIL_PASSWORD=real-mail-password
GOOGLE_CLIENT_ID=real-google-client-id
GOOGLE_PROJECT_ID=real-firebase-project-id
GOOGLE_APPLICATION_CREDENTIALS=/path/to/firebase-service-account.json
```

Production disables Swagger UI and OpenAPI docs:

```yaml
springdoc:
  swagger-ui:
    enabled: false
  api-docs:
    enabled: false
```

## Tests: `test`

Tests use `src/test/resources/application-test.yaml`.

The test profile uses:

- H2 in-memory database
- `ddl-auto: create-drop`
- Liquibase disabled
- Test JWT and mail values

This keeps automated tests independent from local PostgreSQL.

### PostgreSQL integration verification

The PostgreSQL-only verification tests run against a disposable PostgreSQL 16 database when
`GENLINK_TEST_POSTGRES_URL` is set. CI provisions `postgres:16` with these non-production values:

```text
PostgreSQL version: 16.x
Database: campuslink_test
Username: postgres
Password: postgres
JDBC URL: jdbc:postgresql://localhost:5432/campuslink_test
GENLINK_TEST_POSTGRES_URL=jdbc:postgresql://localhost:5432/campuslink_test
GENLINK_TEST_POSTGRES_USERNAME=postgres
GENLINK_TEST_POSTGRES_PASSWORD=postgres
```

The wallet race test enables Liquibase with
`classpath:db/changelog/db.changelog-master.yaml` and uses `ddl-auto=validate`, proving the
application starts against the migrated schema (including 0047 then 0048). The migration test
disables Liquibase intentionally, applies the real 0048 SQL to an isolated PostgreSQL schema,
and verifies the backfill and index. The concurrent wallet mutation tests use the same
PostgreSQL variables in CI; without them they continue to use the H2 test profile locally.

To reproduce locally with a disposable container:

```powershell
docker run --rm --name genlink-postgres-test `
  -e POSTGRES_DB=campuslink_test `
  -e POSTGRES_USER=postgres `
  -e POSTGRES_PASSWORD=postgres `
  -p 5432:5432 postgres:16
$env:GENLINK_TEST_POSTGRES_URL="jdbc:postgresql://localhost:5432/campuslink_test"
$env:GENLINK_TEST_POSTGRES_USERNAME="postgres"
$env:GENLINK_TEST_POSTGRES_PASSWORD="postgres"
./gradlew clean test
```

Never point these variables at a production database. Use a disposable database or schema.

## Environment Variables

| Variable | Required in `prod` | Purpose |
|---|---:|---|
| `SPRING_PROFILES_ACTIVE` | Yes | Selects `dev`, `stack`, or `prod` |
| `DB_URL` | Yes | JDBC database URL |
| `DB_USERNAME` | Yes | Database username |
| `DB_PASSWORD` | Yes | Database password |
| `JWT_SECRET` | Yes | Signs JWT access and refresh tokens |
| `JWT_ISSUER` | No | JWT issuer name |
| `JWT_ACCESS_EXPIRATION_MS` | No | Access token lifetime |
| `JWT_REFRESH_EXPIRATION_MS` | No | Refresh token lifetime |
| `MAIL_HOST` | No | SMTP host, defaults to Gmail SMTP |
| `MAIL_PORT` | No | SMTP port, defaults to `587` |
| `MAIL_USERNAME` | Yes | SMTP username and sender address |
| `MAIL_PASSWORD` | Yes | SMTP password or app password |
| `GOOGLE_CLIENT_ID` | Yes | Google OAuth client ID |
| `GOOGLE_PROJECT_ID` | Yes | Firebase project ID |
| `GOOGLE_APPLICATION_CREDENTIALS` | Yes for Google login | Firebase service account JSON path |
| `APP_DEFAULT_LANGUAGE` | No | Default language, defaults to `ENGLISH` |

## Recommended Improvements

- Add the backend service to `infrastructure/docker-compose.yml` if the team wants a full `stack` runtime. Right now Compose starts PostgreSQL only.
- Add a `.env.example` file with non-secret placeholder values so teammates know what to copy.
- Keep `prod` free of default passwords, tokens, and local database values.
- Keep Swagger enabled only in `dev` and `stack`, disabled in `prod`.
- Use a different database name or schema for each teammate if multiple developers share one PostgreSQL instance.
- Replace the local default JWT secrets before using any shared environment.
