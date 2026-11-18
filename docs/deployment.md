# Deploying across networked machines

The default Compose file runs all containers on one host for development. It demonstrates process-level partial failures, not isolation from that host failing. The requirements recommend networked VMs or physical machines; use the layout below for the final demonstration.

## Suggested topology

| Machine | Example private IP | Run |
| --- | --- | --- |
| View | 192.168.50.10 | web-app |
| API | 192.168.50.11 | api-orchestrator |
| Identity | 192.168.50.12 | identity-service and identity-db |
| Courses | 192.168.50.13 | course-service and course-db |
| Grades | 192.168.50.14 | grade-service and grade-db |

Use Docker Compose 2.24.4+ on each machine. Actual private addresses must match your lab. Grouping identity, courses, or grades on one machine combines their machine failure boundaries; document that if hardware limits require it.

## Preparation

1. Copy source and build images on each host, or build/tag images once and transfer them through your chosen image archive/registry workflow.
2. Run credential setup **once**, then securely copy matching public keys to every host. Only the identity host receives `private.pem`. Keep local `.env` secrets outside the source archive. Each domain host needs its own database password; identity also needs `DEMO_PASSWORD`.
3. Because the complete base Compose file is interpolated before a selected service starts, provide all four credential variable names in each machine's `.env`; unused variables can contain independent placeholders. Never distribute the private key outside the identity node.
4. On Linux, grant UID 10001 read access to the private key as described in the README. Keep the public key readable by the runtime account. Setup does not rotate keys or overwrite an existing pair.

The Java-to-Java lab traffic is plaintext gRPC. Restrict the private network and host firewalls to the listed peers. For an untrusted network, add gRPC TLS and an HTTPS reverse proxy before deployment. Set `COOKIE_SECURE=true` when the browser uses HTTPS.

## Host commands

On the identity host:

```powershell
docker compose -f compose.yaml -f deployment/compose.nodes.yaml up -d --build identity-service
```

On the course host:

```powershell
docker compose -f compose.yaml -f deployment/compose.nodes.yaml up -d --build course-service
```

On the grade host:

```powershell
docker compose -f compose.yaml -f deployment/compose.nodes.yaml up -d --build grade-service
```

Their database dependencies start automatically. On the API host append these non-secret targets to its `.env`:

```dotenv
IDENTITY_TARGET=192.168.50.12:9091
COURSE_TARGET=192.168.50.13:9092
GRADE_TARGET=192.168.50.14:9093
```

Then start:

```powershell
docker compose -f compose.yaml -f deployment/compose.nodes.yaml up -d --build api-orchestrator
```

On the view host, set `ORCHESTRATOR_TARGET=192.168.50.11:9090` in `.env`, then:

```powershell
docker compose -f compose.yaml -f deployment/compose.nodes.yaml up -d --build web-app
```

Open `http://192.168.50.10:8080`. Allow inbound TCP 8080 to the view from your demonstration browser, 9090 to the API from the view, and the domain gRPC ports from the API. Do not expose PostgreSQL ports to the LAN; they are bound to loopback in the base file. Containers use their local Docker network for database traffic.

The override file publishes the backend gRPC listener using host ports. It is **not needed** for the single-host development stack. `!override` replaces the loopback web binding for the networked demo; it requires the specified Compose version.

## Readiness, recovery, and rollback

PostgreSQL readiness checks gate each owning service. Java services may still be starting after `up` returns. Review `docker compose logs --tail 60 <service>` and load its relevant page. A successful login, catalog query, and grade-history query establish the three domain paths. Process-running status alone does not establish application readiness.

Stop/start one domain service or power off its VM, and follow the failure matrix in `demo.md`. Recovery requires its database and service to be reachable; clients reconnect and the retry link reloads the affected page. Database volumes retain existing enrollments and published grades.

For changes, build/tag a new immutable image, preserve the old tag, and back up the affected PostgreSQL database before migration. Current migrations initialize the schema only. Future releases must use additive, compatible migrations if an older image is to run against the upgraded database; reverting an image alone does not reverse database changes. No automatic multi-host failover is included.
