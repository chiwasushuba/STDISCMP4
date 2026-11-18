# Northstar · Distributed enrollment portal

A Java/Spring Boot enrollment system with a standalone MVC view node, a thin gRPC API orchestrator, and three independently persisted domain services.

## Start here

Requirements: Docker Desktop with Linux containers, Docker Compose 2.24.4+, and Java 21+ for credential setup. The Maven wrapper downloads Maven 3.9.11. Docker builds use Java 21; Java 22 also works for local development.

From PowerShell in this directory:

```powershell
./scripts/setup.ps1
docker compose up -d --build
```

Open **http://localhost:8080**. The first build downloads dependencies and Java images; allow services to finish starting before signing in. A temporary feature-unavailable message during startup can be retried.

Demo accounts:

| Email | Role |
| --- | --- |
| student@northstar.edu | Alex Morgan, student 2024-001 |
| student2@northstar.edu | Sam Rivera, student 2024-002 |
| faculty@northstar.edu | Dr. Jordan Reyes, faculty FAC-001 |

Their shared initial password is the randomly generated **DEMO_PASSWORD** in your local `.env`. Open that file locally to retrieve it. Setup preserves existing keys and credentials. Demo accounts are inserted only when `DEMO_ENABLED=true`; changing the environment password after the account exists does not reset it.

No credentials or private keys belong in source control. The private key is mounted only in the identity service; all nodes receive the public key. Keep `.env` and `secrets/` with the deployment, outside source archives.

On Linux/macOS, credential setup is `java scripts/GenerateKeys.java`. On Linux Docker hosts, allow the runtime UID to read its private-key mount: `sudo chown 10001:10001 secrets/private.pem` followed by `sudo chmod 600 secrets/private.pem`. The runtime image runs as UID 10001, not root. Windows Docker Desktop handles bind mounts separately.

## Local Java development

This starts the three PostgreSQL containers and five Java processes, without containerizing the Java services:

```powershell
./scripts/setup.ps1
./scripts/run-local.ps1
```

Logs are in `.tools/logs/`; process IDs are recorded in `.tools/local-processes.json`. Stop those Java processes with `./scripts/stop-local.ps1`. Database containers and persistent volumes remain intact. Do not run the local Java stack and the full Docker stack simultaneously: the web port will conflict.

Build and test independently:

```powershell
./mvnw.cmd -B verify
```

Tests use real PostgreSQL through Testcontainers, so Docker must be running. No local `.env` or signing keys are needed for tests. On Unix use `sh ./mvnw -B verify`. A compile/package-only build is `./mvnw.cmd -B -DskipTests package`; it does not establish test success.

Browser checks use Node.js and Playwright as development-only tools. Start the full Docker stack first, then:

```powershell
npm ci
npx playwright install chromium
npm run test:e2e -- --project=journeys
$env:TEST_FAULTS = '1'
npm run test:faults
Remove-Item Env:TEST_FAULTS
```

Fault tests intentionally stop and restart local Northstar containers, then check unaffected features and recovery. Run them against the local Docker stack only. They restore stopped containers in `finally` blocks. The journey tests use the synthetic demo accounts and update their enrollment/grade records. Browser captures are written to `.evidence/browser/`; login credentials are read locally and tracing is disabled.

Formatting: `npm run format:web:check` checks HTML/CSS/JS. `./mvnw.cmd -B com.spotify.fmt:fmt-maven-plugin:2.29:check` checks Java; use the plugin's `format` goal to apply formatting.

## What is implemented

- Login/logout, signed 15-minute JWT access tokens, persistent eight-hour refresh sessions, rotating refresh tokens, and student/faculty permissions.
- Searchable course catalog, department filtering, open/full/closed states, seat counts, and enrollment receipts.
- Transactional enrollment with duplicate protection and per-course locking to prevent overbooking across service replicas.
- Student-owned historical grades and semester filtering.
- Faculty class rosters, CSV download, upload preview, explicit publish confirmation, and atomic grade updates.
- Independent feature errors, bounded gRPC calls, persistent databases, Flyway migrations, and Hikari connection pooling.
- Responsive student and faculty workspaces with CSRF-protected forms, keyboard focus styling, and no external asset dependency.

CSV format:

```csv
student_id,grade
2024-001,1.25
2024-002,INC
```

Download the selected class roster to get valid student IDs. Blank grades in the downloaded file must be filled or those rows removed before uploading. Maximum upload: 256 KB and 500 rows. This intentionally simple CSV dialect has two unquoted columns; embedded commas and multiline fields are rejected. Allowed grades are 1.00–3.00 in 0.25 steps, 4.00, 5.00, INC, and DRP. A bad value, duplicate student, or unknown roster member rejects the entire publish operation.

## Project layout

```text
contracts/          Versioned protobuf APIs and generated Java stubs
service-common/     JWT validation, RPC security, server lifecycle, error mapping
web-app/            Spring MVC controllers, Thymeleaf views, CSS, browser sessions
api-orchestrator/   gRPC controllers forwarding requests to one owning service
identity-service/   Accounts, password verification, JWT signing, refresh sessions
course-service/     Catalog, enrollment, seat allocation
grade-service/      Teaching assignments, rosters, historical and uploaded grades
deployment/         Configuration for separate networked machines
scripts/            Local credentials and process management
docs/               Architecture, deployment, and demonstration guide
```

Each domain service keeps its business rules in `*Service` and exposes them through `*Controller` gRPC handlers. JDBC is used directly rather than adding repositories that only forward identical calls. No service reads another service's tables.

## Fault tolerance and deployment

See [architecture](docs/architecture.md), [networked-machine setup](docs/deployment.md), and [demonstration scenarios](docs/demo.md).

The core design isolates domain failures. It does **not** replicate the web node, orchestrator, or databases. The web node and orchestrator are shared points of failure. Database replication is an optional bonus that has not been implemented; persistence volumes and connection pooling are implemented.

Internal gRPC is plaintext on the private Docker/lab network. The default web listener is HTTP on loopback. For access beyond a trusted demonstration network, configure TLS on the web and gRPC boundaries, enable secure cookies, and provide account provisioning and login throttling before public deployment.

Useful commands:

```powershell
docker compose ps
docker compose logs --tail 80 grade-service
docker compose stop grade-service
docker compose start grade-service
docker compose down
```

`down` preserves the databases. Do not add `--volumes` unless you intend to delete all enrollment, session, and grade data.

Create a source-only submission archive with `./scripts/package-source.ps1`. It includes source, contracts, tests, build scripts, and documentation. It excludes local credentials, signing keys, databases, downloaded tools, dependencies, and build outputs. The recipient runs setup to generate their own local credentials.
