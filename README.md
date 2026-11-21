# AnimoRoll · Distributed enrollment portal

A Vite enrollment portal served by a Spring MVC web node, with a thin gRPC API orchestrator and three independently persisted Spring Boot domain services.

## Build and run with Docker

Install Docker Desktop with Linux containers, Docker Compose 2.24.4 or newer, and JDK 21 or newer. Docker supplies Node.js, Maven, and Java 21 for the image builds. The host JDK runs the credential setup.

From the repository root, generate `.env` and the JWT signing keys. Use `java scripts/GenerateKeys.java` on macOS or Linux.

```powershell
./scripts/setup.ps1
```

If you want to use the example password below, set it before the first start. Then build the images, start the applications and databases, and check their status.

```powershell
docker compose build
docker compose up -d
docker compose ps
```

Docker builds the Vite frontend first. Maven compiles the Java services and bundles the Vite output into the `web-app` JAR. The first build downloads images and dependencies. Once the services finish starting, open **http://localhost:8080**. If a service is still initializing, use the retry control after it starts.

### Example login credentials

| Email | Role |
| --- | --- |
| `student@northstar.edu` | Alex Morgan, student 2024-001 |
| `student2@northstar.edu` | Sam Rivera, student 2024-002 |
| `faculty@northstar.edu` | Dr. Jordan Reyes, faculty FAC-001 |

All three accounts share the initial password from `DEMO_PASSWORD` in your local `.env`. Setup generates a random value. To use a fixed example on a fresh, local demo database, replace that line after setup and before starting the app for the first time.

```dotenv
DEMO_PASSWORD=AnimoRoll-Demo-2026
```

For example, sign in as `student@northstar.edu` with `AnimoRoll-Demo-2026` after making that change. The identity service seeds demo accounts when `DEMO_ENABLED=true`, which the local Compose file enables. Existing accounts keep the password they were created with. Setup also preserves existing `.env` and key files.

Keep `.env` and `secrets/` with your local installation. The identity service uses the private key to sign tokens. Services use the public key to verify them. On Linux Docker hosts, grant the container user read access to the private key with `sudo chown 10001:10001 secrets/private.pem` and `sudo chmod 600 secrets/private.pem`.

## Compile and run locally on Windows

Install JDK 21 or newer, Node.js 22.12+ or 24 LTS, and Docker Desktop for the three PostgreSQL databases. The Maven wrapper downloads Maven 3.9.11. If the full Docker stack is running, stop it with `docker compose down` to free port 8080 before launching the local Java processes.

Run setup, then build the application from the repository root in PowerShell. For the fixed example login, edit `.env` after setup and before running the final command.

```powershell
./scripts/setup.ps1
npm.cmd --prefix frontend ci
npm.cmd --prefix frontend run build
./mvnw.cmd -B -DskipTests clean package
./scripts/run-local.ps1 -SkipBuild
```

Vite writes `frontend/dist/`. Maven writes the service JARs into each module’s `target/` folder and includes the Vite bundle in `web-app/target/web-app-1.0.0.jar`. The last command starts the PostgreSQL containers and launches the five Java applications. Open **http://localhost:8080**.

After setup, `./scripts/run-local.ps1` also handles compilation and startup in one command. Local logs are in `.tools/logs/`. Run `./scripts/stop-local.ps1` to stop the Java processes. The database containers and volumes remain available.

### Live frontend development

For live frontend changes, keep Spring running on port 8080 from either stack and run:

```powershell
npm --prefix frontend ci
npm --prefix frontend run dev
```

Open **http://127.0.0.1:5173**. Vite proxies `/api` to Spring. In a packaged build, Spring serves the UI and API on port 8080.

## Run the tests

Run the JVM tests with Docker running so Testcontainers can start PostgreSQL.

```powershell
./mvnw.cmd -B verify
```

On macOS or Linux, use `sh ./mvnw -B verify`.

Browser checks use Node.js and Playwright as development-only tools. Start the full Docker stack first, then:

```powershell
npm ci
npx playwright install chromium
npm run test:e2e -- --project=journeys
$env:TEST_FAULTS = '1'
npm run test:faults
Remove-Item Env:TEST_FAULTS
```

Fault tests intentionally stop and restart local Docker containers, then check unaffected features and recovery. Run them against the local Docker stack only. They restore stopped containers in `finally` blocks. The journey tests use the synthetic demo accounts and update their enrollment and grade records. Browser captures are written to `.evidence/browser/`. The tests read credentials locally and disable tracing.

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
frontend/           Vite project with plain HTML, CSS, and JavaScript views
web-app/            Spring MVC browser endpoints, sessions, static asset hosting
api-orchestrator/   gRPC controllers forwarding requests to one owning service
identity-service/   Accounts, password verification, JWT signing, refresh sessions
course-service/     Catalog, enrollment, seat allocation
grade-service/      Teaching assignments, rosters, historical and uploaded grades
deployment/         Configuration for separate networked machines
scripts/            Local credentials and process management
docs/               Architecture, deployment, and demonstration guide
tests/              Playwright journeys and service outage checks
secrets/            Locally generated JWT keys, excluded from Git
deliverables/       Submission source archives, demo video, and slides
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

## Submission deliverables

The [deliverables folder](deliverables/) contains the demo video, slides, and dated source archives. Run `./scripts/package-source.ps1` to create an `animoroll-source-*.zip` from the current Vite and Spring sources. The archive includes this README, contracts, tests, build scripts, and deployment instructions. It excludes credentials, databases, downloaded tools, dependencies, and build outputs. The recipient runs setup to generate local credentials.
