# Demonstration and acceptance scenarios

Follow the [quick start](../README.md#quick-start-with-a-populated-database) to start the demo and find the generated password in `.env`. Use the synthetic student and faculty accounts listed there. A fresh database has six courses, historical grades, and the CS 301 roster ready for the walkthrough. The instructor's final test cases have not yet been supplied. Map them onto or extend these scenarios when available.

## Suggested recording sequence

1. Show the running nodes and name each responsibility. For a distributed-machine submission show the host/VM assignment, not just several containers on one computer.
2. Sign in as Alex. Open the course catalog, search for Distributed Systems, enroll, then open My enrollment and refresh. The course appears once and its seat count decreases once.
3. Show the closed Human–Computer Interaction offering. Demonstrate that it cannot be enrolled in through the UI. Automated backend tests also cover closed offerings and last-seat races.
4. Open Grade history. Filter by semester and show that records belong to Alex.
5. Sign out, then sign in as faculty. Open CS 301 for the current semester. Download the CSV roster, enter valid grades, upload, review the preview, and confirm publish. The roster shows saved values.
6. Upload a file containing a valid first row and an unknown student or invalid grade in the second row. Publish fails and none of that file's changes persist.
7. Sign back in as Alex and show the published current-semester grade.
8. Demonstrate each partial failure below, followed by recovery.

## Fault injection matrix

For the Docker stack, run the listed commands in another terminal. For the local Java stack, use the corresponding recorded process ID in `.tools/local-processes.json` and restart through the local scripts after stopping all local nodes, or use the full Docker stack for a smoother demonstration.

| Action | Inspect | Expected |
| --- | --- | --- |
| `docker compose stop grade-service` | Student dashboard, catalog, enrollment, grade history | Course panels and enrollment work; grade panels explain temporary unavailability |
| `docker compose start grade-service` | Reload grade history | Saved grades return |
| `docker compose stop course-service` | Dashboard and grade history | Course panel fails; published grades still work |
| `docker compose start course-service` | Catalog and My enrollment | Existing enrollments return |
| `docker compose stop identity-service` | Already authenticated student, then a separate fresh browser context | Existing valid JWT works for courses/grades; new login fails |
| `docker compose start identity-service` | Fresh login | Login recovers |
| `docker compose stop grade-db` | Grade history and catalog | Grades fail within bounded time; catalog works |
| `docker compose start grade-db` | Grade history after reconnection | Saved grades return |
| `docker compose stop api-orchestrator` | Any protected page | All backend features fail gracefully; this shared failure boundary is acknowledged |

Run start/recovery after every test before the next independent case. If stopping identity lasts until the token needs refresh (14 minutes in the web node), other authenticated features also require identity again. The short-lived-token tradeoff is intentional.

For machine-failure evidence, power off only the course or grade VM in the networked deployment, show unaffected features, then power it back on. The default single-host stack cannot prove host-level fault tolerance.

## Automated checks

`./mvnw.cmd -B verify` uses PostgreSQL Testcontainers to check:

- Persistent identity claims, signed-token tamper rejection, refresh rotation, logout revocation, and incorrect passwords.
- Duplicate enrollment, closed-course rejection, faculty rejection, last-seat concurrency, search, and student-specific enrollment state.
- Whole-file grade rollback, assignment permissions, student-specific grade visibility, and grade value/duplicate validation.
- CSV headers, UTF-8 BOM, Windows line endings, duplicates, extra columns, and missing grades.

Browser and outage evidence is recorded separately in `verification.md`. Passing domain tests alone does not establish rendered appearance or cross-machine deployment success.
