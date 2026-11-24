# davamly

davamly tracks daily completion habits and counters. Spring Boot serves the static frontend and JSON API from the same origin. Spring Security handles access control and CSRF. Spring Session stores sessions in the database. Spring Data JPA persists accounts, habits, daily progress and activity, and Flyway manages the schema.

## Run locally

Use a Java 21 JDK. The Maven Wrapper downloads the project's Maven version.

```powershell
java -version
.\mvnw.cmd spring-boot:run
```

Open `http://localhost:8080`. By default H2 stores data under `.data/davamly.mv.db`. If the legacy `.data/streaky.mv.db` exists and the new database does not, the app opens the existing file. Keep a backup before upgrading a populated database. A browser upgrades its saved theme and view preferences on the same origin. Browser data on a different origin remains isolated by the browser.

For PostgreSQL development, copy `.env.example` to `.env` and set `DB_PASSWORD` to a locally generated value. Start the database, then set the app's environment in your shell. Docker Compose reads `.env` automatically. The Maven Wrapper uses the shell environment.

```powershell
Copy-Item .env.example .env
# Set DB_PASSWORD in .env before starting Compose.
docker compose up -d --wait postgres
$env:DB_URL = 'jdbc:postgresql://127.0.0.1:5432/davamly'
$env:DB_USER = 'davamly'
$env:DB_PASSWORD = ((Get-Content .env | Where-Object { $_ -match '^DB_PASSWORD=' }) -split '=', 2)[1]
.\mvnw.cmd spring-boot:run
```

To use the production datasource and security settings on your machine, set `SPRING_PROFILES_ACTIVE=production` with `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` and `DB_PASSWORD`. Use HTTPS for browser sessions in this profile.

## Tests and builds

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd verify
.\mvnw.cmd -DskipTests package
docker build --pull -t davamly:local .
```

`target/davamly.jar` is the executable JAR. The H2 suite and unit tests run by default. Set `RUN_POSTGRES_IT=true`, `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` and `DB_PASSWORD` for `verify` to run the same API contract, PostgreSQL-specific concurrency tests and secure cookie checks against a disposable PostgreSQL database. Start with an empty database to verify migration from scratch. GitHub Actions provisions its own database and checks the production image against a second empty database.

Run the image with the production database variables and a network route to PostgreSQL. For a development PostgreSQL started with the Compose file, Docker Desktop can reach the host using `host.docker.internal` if its network permits the published loopback port. A shared Docker network also works using the PostgreSQL service name and container port. Set variables locally and pass their names to Docker.

```powershell
docker run --rm -p 127.0.0.1:8080:8080 --env DB_HOST --env DB_PORT --env DB_NAME --env DB_USER --env DB_PASSWORD davamly:local
curl.exe --fail http://127.0.0.1:8080/api/health
curl.exe --fail http://127.0.0.1:8080/api/ready
python scripts/smoke.py http://127.0.0.1:8080 --loopback-http
```

## Configuration

| Variable | Purpose |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | Set to `production` for hosting. The image sets it by default. |
| `PORT` | HTTP port. Defaults to `8080`. The Render Blueprint sets `10000`. |
| `DB_HOST`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | Required in production. The app uses PostgreSQL and passes the username and password separately to the driver. |
| `DB_PORT` | Production PostgreSQL port. Defaults to `5432`. |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Optional JDBC override and credentials for local development. `DB_URL` is ignored by the production datasource. |
| `DB_POOL_MAX_SIZE`, `DB_POOL_MIN_IDLE` | Hikari pool limits. Defaults are `5` and `0`. Connection acquisition times out after 10 seconds. Production sets a 5-second driver connect timeout and a 30-second socket timeout. |
| `COOKIE_SECURE` | Enable secure cookies in local HTTPS development. Production always enables Secure. Session cookies also use HttpOnly and SameSite=Lax. |
| `ALLOWED_ORIGINS` | Comma-separated exact origins for credentialed API CORS. Leave blank for the bundled same-origin frontend. Cross-site cookies remain subject to browser and SameSite rules. |
| `AUTH_RATE_LIMIT_ENABLED` | Local development toggle. Production enables the rate limiter. By default it allows 20 login attempts per IP and 10 per username per 15 minutes, and 5 registrations per IP per hour. Counters are per application instance. |
| `RUN_POSTGRES_IT` | Set to `true` for the opt-in PostgreSQL integration suite. Use a dedicated test database. |

Keep `.env`, database files and secret values out of Git and build contexts. Use a local secret store or hosting environment variables. Avoid including credentials inside JDBC URLs. Production fails during startup if required database configuration is missing or migrations and schema validation fail.

## Migrations and operations

Flyway applies V1 (application tables) and V2 (JDBC sessions) before Hibernate validates the mapped schema. Add schema changes as new versioned migrations. Leave applied SQL files intact. Automatic baseline is disabled in production. For an existing schema without Flyway history, compare it with V1 and agree on an explicit baseline after a backup.

`GET /api/health` checks that the HTTP application is alive. `GET /api/ready` checks a live database query and returns `503` when the database is unavailable. Account deletion removes the account's habits, progress, activity and sessions. Individual habit deletion removes its progress and preserves activity snapshots.

### Render

1. Connect the existing GitHub repository to Render. Create a Blueprint using the reviewed deployment branch. Before applying it, verify the Frankfurt region, web and PostgreSQL plans, and billing in Render. The configured durable plans are paid.
2. Apply `render.yaml`. The web service and managed PostgreSQL are in the same region. The database allows internal connections only. The Blueprint injects individual database properties, allowing the app to construct the PostgreSQL JDBC address without embedding credentials.
3. Check the service Events and Logs for successful startup and Flyway migration messages. Check `/api/health` and `/api/ready` over HTTPS.
4. Run `python scripts/smoke.py https://<render-service-host>`. The script registers a unique account and removes it at the end.

After the PR is merged, change the Blueprint and service to track `main` in Render. For an application rollback, select the last healthy deployment from the service's Events page and confirm health and readiness. V1 and V2 are unchanged so this release needs no schema rollback. Restore from a verified backup only if recovery requires it.

[Blueprint reference](https://render.com/docs/blueprint-spec) · [Compute plans](https://render.com/docs/compute-plans) · [Deploys and rollbacks](https://render.com/docs/deploys)

## API

Use a cookie jar. Fetch `/api/auth/csrf` before writes and send its token in the named header. Fetch again after successful login, which rotates both the session ID and the token. `request.http` contains a sample sequence for an HTTP client.

| Method and path | Behavior |
| --- | --- |
| `GET /api/auth/csrf` | Returns `{ "token": "…", "headerName": "X-CSRF-TOKEN" }` and initializes a session. |
| `POST /api/auth/register` | `{ "username": "example", "password": "<local test password>", "timezone": "Europe/Istanbul" }` → `201` with the user. Duplicate usernames return `409`. |
| `POST /api/auth/login` | Username and password → `200` with the user. Invalid credentials return `401`. |
| `GET /api/auth/me` | Returns `{ id, username, name, bio, timezone }`. |
| `POST /api/auth/logout` | Invalidates the current session → `204`. |
| `PATCH /api/users/me` | `{ "name": "Example", "bio": "", "timezone": "UTC" }` → updated user. |
| `DELETE /api/users/me` | Deletes the current user's account and revokes sessions → `204`. |
| `GET /api/habits` | Returns the user's habits and today's counts in the profile timezone. |
| `POST /api/habits` | `{ "name": "Drink water", "kind": "counter", "target": 5, "unit": "glasses" }` → `201` with the habit. `kind` can be `counter` or `completion`. |
| `PUT /api/habits/{id}` | Updates the habit and returns `{ id, name, description, kind, target, unit, todayCount, percentage, completed }`. |
| `DELETE /api/habits/{id}` | Removes the habit and its progress → `204`. |
| `POST /api/habits/{id}/increment` and `/decrement` | Adjust today's count → `{ date, count, dailyTarget }`. Each request applies one adjustment within the count limits. |
| `PUT /api/habits/{id}/completion` | `{ "completed": true }` sets today's state. Repeating the same value creates no extra activity. |
| `GET /api/habits/{id}/heatmap` | Returns daily `{ date, count, percentage }` values. |
| `GET /api/habits/{id}/statistics` | Returns `{ currentStreak, longestStreak, completedDays }`. Any activity extends the streak. Reaching the target counts as a completed day. |
| `GET /api/activity?page=0&size=30` | Newest first. Returns `{ items, page, size, totalElements, hasNext }`. Page size is 1–100. |

Heatmap and statistics accept `days=1` through `366` or both `start` and `end` as ISO dates. Ranges are inclusive, default to 182 days, and cannot end after today in the user's timezone. Invalid input returns `400`. Missing authentication returns `401`, missing or invalid CSRF returns `403`, and another user's habit returns `404`. Rate limits return `429`. JSON problem responses include a numeric `status` and a `detail` message.
