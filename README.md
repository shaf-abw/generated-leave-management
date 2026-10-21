# Generated Leave Management

First target application for the AI Software Factory. This repository is intentionally separate from the factory control plane.

## Planned MVP

- Angular frontend and Spring Boot REST API.
- PostgreSQL persistence with versioned migrations.
- Demo authentication and employee/manager roles.
- Employee CRUD, leave submission, manager approval/rejection, leave history/status, and a basic dashboard.
- Unit and integration tests, local Docker Compose, and a protected-branch CI workflow.
- Staging deployment only after human approval of the exact build artifact digest.

Use synthetic/demo data only. Production credentials, production deployment, and real employee data are out of scope.

## Current status

MVP implemented per [`docs/implementation-plan.md`](docs/implementation-plan.md) (T01–T14). Staging deployment is not part of this repository.

| Path | Contents |
| --- | --- |
| `backend/` | Spring Boot 3.5 (Java 21) REST API, Flyway migrations, session auth, Testcontainers integration tests, JaCoCo gate (80% lines) |
| `frontend/` | Angular 20 SPA, Karma/Jasmine unit tests with coverage gate (70% lines) |
| `docker-compose.yml` | Local-only stack: `db` (PostgreSQL 16), `backend`, `frontend` (nginx, proxies `/api`) |
| `.github/workflows/` | Plan check and application CI (tests, coverage gates, `docker compose config`); no deploy jobs |

## Run locally with Docker Compose

```bash
cp .env.example .env        # demo-only values; .env is git-ignored
docker compose up -d --build
docker compose ps           # all services report "healthy"
```

Open http://localhost:4200 (or `FRONTEND_PORT` from `.env`). Backend health is exposed through the frontend at http://localhost:4200/actuator/health and on the `/health` page.

### Demo accounts

All accounts are synthetic and share the demo password `DemoPass123!` (stored as a BCrypt hash in the seed migration).

| Email | Role |
| --- | --- |
| `morgan.manager@example.com` | MANAGER (manages Riley, Alex, Sam) |
| `riley.supervisor@example.com` | MANAGER (manages Jordan) |
| `alex.example@example.com` | EMPLOYEE |
| `sam.sample@example.com` | EMPLOYEE |
| `jordan.demo@example.com` | EMPLOYEE |

## Development

```bash
cd backend && ./mvnw -B verify                                   # unit + integration tests (needs Docker for Testcontainers)
cd frontend && npm ci && npm run lint && npm test -- --watch=false --code-coverage && npm run build
cd frontend && npm start                                         # dev server on :4200, proxies /api to localhost:8080
```

## API overview

| Method & path | Access |
| --- | --- |
| `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me` | public / authenticated |
| `GET /api/employees`, `GET /api/employees/{id}` | manager (employees: self only) |
| `POST/PUT/DELETE /api/employees[/{id}]` | manager (DELETE = soft deactivate) |
| `POST /api/leave-requests`, `GET /api/leave-requests/mine`, `POST /api/leave-requests/{id}/cancel` | owner |
| `GET /api/leave-requests/pending`, `POST /api/leave-requests/{id}/approve`, `POST /api/leave-requests/{id}/reject` | manager of the requester |
| `GET /api/leave-requests/history?status=&from=&to=&page=&size=` | own (employee) / own + direct reports (manager) |
| `GET /api/dashboard` | authenticated, role-specific |
