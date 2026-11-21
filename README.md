# AnimoRoll · Distributed enrollment portal

AnimoRoll is a university enrollment demo. Students can browse courses, enroll, and view grades. Faculty can download class rosters and publish grades from a CSV file.

## Quick start with a populated database

### 1. Install the prerequisites

1. Install [Docker Desktop](https://docs.docker.com/desktop/) and open it. On Windows, use Linux containers. On Linux, Docker Engine with the Compose plugin also works.
2. Install [JDK 21](https://adoptium.net/temurin/releases/?version=21) or newer. Open a new terminal and run `java -version` and `docker compose version`. Use Compose 2.24.4 or newer. Docker supplies Node.js, Maven, and PostgreSQL for the demo.

### 2. Open the source folder

On GitHub, choose **Code → Download ZIP**, then extract the ZIP. You can also clone the repository if you have Git.

```sh
git clone https://github.com/chiwasushuba/STDISCMP4.git
cd STDISCMP4
```

Open a terminal in the folder that contains `compose.yaml` and `README.md`. On Windows, open that folder in File Explorer, type `powershell` in the address bar, and press Enter.

### 3. Start the app

**Windows PowerShell**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-demo.ps1
```

**macOS or Linux**

On the first run, generate the local configuration and signing keys. The `chmod` commands keep the `secrets/` folder private on your computer and let the containers read the key files Docker mounts into them.

```sh
java scripts/GenerateKeys.java && chmod 700 secrets && chmod 644 secrets/public.pem secrets/private.pem
```

Then build and start the stack.

```sh
docker compose up --build --wait --wait-timeout 180
```

The first run downloads images and build dependencies. Wait for startup to finish, then open [http://localhost:8080](http://localhost:8080). The three PostgreSQL databases populate automatically on the first run.

### 4. Sign in and check the sample data

1. Open `.env` in the source folder with a text editor. On Windows, you can run `notepad .env`. On macOS and Linux, enable hidden files in your file manager to see it.
2. Find `DEMO_PASSWORD=` and copy the value after `=`. All three demo accounts use this generated password when the databases are first created. Keep `.env` and `secrets/` with this installation.
3. Sign in with one of the accounts below.

   | Email | Role |
   | --- | --- |
   | `student@animoroll.edu` | Alex Morgan, student 2024-001 |
   | `student2@animoroll.edu` | Sam Rivera, student 2024-002 |
   | `faculty@animoroll.edu` | Dr. Jordan Reyes, faculty FAC-001 |

4. As the student, open **Course catalog** to find **Distributed Systems** and **Grade history** to see completed courses. **My enrollment** starts empty so you can try enrolling.
5. Sign out, sign in as faculty, and select **CS 301**. The current roster starts with Alex Morgan and Sam Rivera, ready for a grade upload. See the [demo walkthrough](docs/demo.md) for a full run-through.

Every fresh installation includes three demo accounts, six courses, five published grade rows, and two ungraded rows in the CS 301 roster. Re-running startup keeps existing credentials, enrollments, and grades. Changing `DEMO_PASSWORD` in `.env` after the first start leaves existing account passwords as they were.

### 5. Stop the app and come back later

```sh
docker compose down
```

This stops the app and keeps your data. To resume, repeat [step 3](#3-start-the-app).

To reset a **disposable local demo**, run `docker compose down --volumes`, then repeat step 3. The reset deletes all three databases, including any enrollments and grades you added. Starting again restores the original sample data.

### If startup or login fails

| What you see | What to do |
| --- | --- |
| Docker is unavailable | Open Docker Desktop, wait for the engine to start, and repeat step 3. |
| `java` is missing or too old | Install a JDK, open a new terminal, and confirm `java -version` shows 21 or newer. |
| `port is already allocated` | Run `docker ps` to find the container using port 8080 or 5541–5543. If it is the old `northstar` stack, follow the upgrade instructions below or stop it with `docker compose -p northstar down` before starting the fresh demo. |
| A service fails to become healthy | Run `docker compose ps -a` and `docker compose logs --tail 80` from the source folder to find the failing service and its startup error. |
| The login form rejects your password | Use a demo email from the table and the password from this installation's `.env`. If that value was edited after the database was created, use the password that was set on first startup. |

### Upgrade an existing Northstar installation

To reuse the PostgreSQL volumes from the old Compose project, rebuild all five application nodes together under the existing project name.

```powershell
docker compose -p northstar up --build --wait --wait-timeout 180
```

Use `-p northstar` with subsequent Compose commands for that installation. The default `animoroll` project creates its own volumes. The identity migration updates the three demo emails in the existing database while preserving account IDs, passwords, and linked records. Sign in again with the new email after the upgrade. For a multi-machine setup, deploy the rebuilt nodes together because their gRPC service names and JWT issuer and audience changed.

## Compile and run locally on Windows

Install JDK 21 or newer, Node.js 22.12+ or 24 LTS, and Docker Desktop for the three PostgreSQL databases. The Maven wrapper downloads Maven 3.9.11. If the full Docker stack is running, stop it with `docker compose down` to free port 8080 before launching the local Java processes.

1. Open PowerShell in the source folder and generate the credentials.

   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\setup.ps1
   ```

2. Build the Vite frontend and Java services, start PostgreSQL in Docker, and launch the five Java processes on Windows.

   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-local.ps1
   ```

3. Open [http://localhost:8080](http://localhost:8080) and follow the sign-in steps above. If the browser cannot connect yet, check `.tools/logs/web.log` for startup progress. Use `-SkipBuild` when rerunning the launcher with existing build outputs.
4. To stop the Java processes, run the command below. Then run `docker compose down` if you also want to stop the database containers. Your database data is saved in Docker volumes.

   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\stop-local.ps1
   ```

### Live frontend development

For live frontend changes, keep Spring running on port 8080 from either stack and run:

```powershell
npm.cmd --prefix frontend ci
npm.cmd --prefix frontend run dev
```

Open **http://127.0.0.1:5173**. Vite proxies `/api` to Spring. In a packaged build, Spring serves the UI and API on port 8080.

## Run the tests

Run the JVM tests with Docker running so Testcontainers can start PostgreSQL.

```powershell
./mvnw.cmd -B verify
```

On macOS or Linux, use `sh ./mvnw -B verify`.

Browser checks use Node.js and Playwright. Start the app with the [quick start](#quick-start-with-a-populated-database) before running these commands in PowerShell.

```powershell
npm.cmd ci
npx.cmd playwright install chromium
npm.cmd run test:e2e -- --project=journeys
$env:TEST_FAULTS = '1'
npm.cmd run test:faults
Remove-Item Env:TEST_FAULTS
```

Run fault tests against the default local Compose project (`animoroll`). They stop and restart its containers to check recovery and verify that other features keep working. The journey tests update enrollment and grade records for the demo accounts. Browser captures go in `.evidence/browser/`. The tests read credentials locally and disable tracing.

Run `npm.cmd run format:web:check` to check HTML, CSS, and JavaScript formatting. Run `./mvnw.cmd -B com.spotify.fmt:fmt-maven-plugin:2.29:check` to check Java formatting.

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
frontend/           Vanilla Vite frontend
  src/pages/        Login, dashboard, catalog, enrollment, grades, and faculty pages
  src/components/   Workspace shell, course grid, and loading and error states
  src/lib/          Shared API, DOM, and form helpers
  src/styles/       Shared styles and CSS imports for pages and components
web-app/            Spring MVC browser endpoints, sessions, static asset hosting
api-orchestrator/   gRPC controllers forwarding requests to one owning service
identity-service/   Accounts, password verification, JWT signing, refresh sessions
course-service/     Catalog, enrollment, seat allocation
grade-service/      Teaching assignments, rosters, historical and uploaded grades
deployment/         Configuration for separate networked machines
scripts/            Demo launcher, local credentials, and process management
docs/               Architecture, deployment, and demonstration guide
tests/              Playwright journeys and service outage checks
secrets/            Locally generated JWT keys, excluded from Git
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
```

## Create a source archive

Run `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\package-source.ps1` to create `deliverables/animoroll-source-*.zip`. The archive includes the source, tests, build scripts, and this guide. The recipient can extract it and follow the [quick start](#quick-start-with-a-populated-database).
