# Leave Management MVP — Implementation Plan

This plan breaks the MVP described in the [README](../README.md) into
dependency-ordered tasks. It is planning only: no application code is part of
this change.

## Conventions

- Stack: Angular frontend (`frontend/`), Spring Boot REST API (`backend/`),
  PostgreSQL with Flyway migrations, Docker Compose for local runs.
- Data: synthetic/demo data only. No real employee data, no production
  credentials, no secrets committed. Demo credentials live only in seed
  migrations or `.env.example` and are clearly marked as demo-only.
- Roles: `EMPLOYEE` and `MANAGER`. Every employee has at most one manager.
- Task format (enforced by `scripts/verify_plan.py` in CI):
  - Each task is a level-3 heading `### T<NN>: <title>`, numbered in order.
  - Each task has a `**Depends on:**` line listing earlier task IDs or `none`.
    A task may only depend on tasks that appear before it, so the document
    order is a valid execution order.
  - Each task has an `**Acceptance criteria:**` list with at least one
    `- [ ]` item. Criteria are phrased as checks that a command, test, or HTTP
    call can verify.

## Dependency overview

| Task | Title | Depends on |
| ---- | ----- | ---------- |
| T01 | Project scaffolding | none |
| T02 | Database schema and migrations | T01 |
| T03 | Authentication and roles | T02 |
| T04 | Employee CRUD API | T03 |
| T05 | Employee CRUD UI | T04 |
| T06 | Leave request submission API | T04 |
| T07 | Leave request submission UI | T05, T06 |
| T08 | Manager decisions API | T06 |
| T09 | Manager decisions UI | T07, T08 |
| T10 | Leave history and status | T08, T09 |
| T11 | Dashboard | T10 |
| T12 | Test suite and coverage gate | T11 |
| T13 | Docker Compose local environment | T11 |
| T14 | Application CI workflow | T12, T13 |

## Tasks

### T01: Project scaffolding

**Depends on:** none

**Scope:** Create the Spring Boot backend (Java 21, Maven wrapper) under
`backend/` and the Angular frontend under `frontend/`, each with a health
endpoint/page and lint configuration. No business features.

**Acceptance criteria:**

- [ ] `cd backend && ./mvnw -B verify` exits 0.
- [ ] `cd frontend && npm ci && npm run lint && npm run build` exits 0.
- [ ] `GET /actuator/health` on a locally started backend returns HTTP 200 with `{"status":"UP"}`.
- [ ] `.env.example` exists and contains only placeholder/demo values; `.env` remains git-ignored.

### T02: Database schema and migrations

**Depends on:** T01

**Scope:** Flyway migrations under `backend/src/main/resources/db/migration`
for `employee` (id, name, email, role, manager_id, active),
`leave_request` (id, employee_id, type, start_date, end_date, reason, status,
decided_by, decision_comment, created_at, decided_at) and an
`app_user` credential table. A separate, clearly named seed migration inserts
synthetic demo users (at least one manager and two employees).

**Acceptance criteria:**

- [ ] An integration test using Testcontainers PostgreSQL applies all migrations from an empty database without error.
- [ ] `leave_request.status` is constrained to `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`.
- [ ] A check constraint rejects rows where `end_date < start_date`.
- [ ] Seed data uses only `example.com` email addresses and obviously fictitious names.

### T03: Authentication and roles

**Depends on:** T02

**Scope:** Spring Security demo authentication (session or JWT) backed by
`app_user`, with roles `EMPLOYEE` and `MANAGER`. Endpoints:
`POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`.
Passwords are stored as BCrypt hashes. Angular login page, auth guard, and
HTTP interceptor.

**Acceptance criteria:**

- [ ] `POST /api/auth/login` with valid demo credentials returns 200; with invalid credentials returns 401.
- [ ] `GET /api/auth/me` returns the current user's id, name, and role; returns 401 when unauthenticated.
- [ ] Any `/api/**` endpoint other than login returns 401 without authentication.
- [ ] A `MANAGER`-only endpoint returns 403 for an `EMPLOYEE` user (covered by an integration test).
- [ ] No plaintext password is stored or logged (verified by test asserting stored value matches BCrypt format).
- [ ] Frontend redirects unauthenticated users to `/login` (covered by a component/guard unit test).

### T04: Employee CRUD API

**Depends on:** T03

**Scope:** REST endpoints `GET/POST /api/employees`,
`GET/PUT/DELETE /api/employees/{id}` with Bean Validation. Delete is a soft
delete (`active = false`). Only `MANAGER` may create, update, or delete;
any authenticated user may read their own record.

**Acceptance criteria:**

- [ ] Create, read, update, and soft-delete each have a passing integration test.
- [ ] Invalid payloads (blank name, malformed email, duplicate email) return 400 with field-level error details.
- [ ] `EMPLOYEE` calling `POST`, `PUT`, or `DELETE` receives 403.
- [ ] `EMPLOYEE` calling `GET /api/employees/{id}` for another employee receives 403.
- [ ] Unknown id returns 404.

### T05: Employee CRUD UI

**Depends on:** T04

**Scope:** Angular employee list, create/edit form, and deactivate action,
visible only to managers.

**Acceptance criteria:**

- [ ] Employee list, form validation, and deactivate flow are covered by passing component unit tests (`npm test`).
- [ ] Employee management navigation is hidden for `EMPLOYEE` users (unit test).
- [ ] Server-side validation errors from T04 are displayed next to the relevant form fields.

### T06: Leave request submission API

**Depends on:** T04

**Scope:** `POST /api/leave-requests` (employee submits for self),
`GET /api/leave-requests/mine`, and `POST /api/leave-requests/{id}/cancel`
(only while `PENDING`). New requests start as `PENDING`.

**Acceptance criteria:**

- [ ] A valid submission returns 201 with `status = PENDING` and is persisted.
- [ ] `endDate` before `startDate`, a start date in the past, or a missing type returns 400.
- [ ] A request overlapping an existing `PENDING` or `APPROVED` request of the same employee returns 409.
- [ ] Cancelling a `PENDING` request sets `CANCELLED`; cancelling any other status returns 409.
- [ ] An employee cannot cancel another employee's request (403).

### T07: Leave request submission UI

**Depends on:** T05, T06

**Scope:** Angular form to submit a leave request and a "My requests" view
with a cancel action for pending requests.

**Acceptance criteria:**

- [ ] Form prevents submission when the end date is before the start date (unit test).
- [ ] Successful submission shows the new request with status `PENDING` in "My requests" (unit test with mocked HTTP).
- [ ] Cancel button is shown only for `PENDING` requests (unit test).

### T08: Manager decisions API

**Depends on:** T06

**Scope:** `GET /api/leave-requests/pending` (requests from the manager's
direct reports), `POST /api/leave-requests/{id}/approve`, and
`POST /api/leave-requests/{id}/reject` (comment required on reject).
Records `decided_by`, `decided_at`, and `decision_comment`.

**Acceptance criteria:**

- [ ] A manager sees only pending requests from their direct reports (integration test with two managers).
- [ ] Approve sets `APPROVED`; reject sets `REJECTED`; both record `decided_by` and `decided_at`.
- [ ] Reject without a comment returns 400.
- [ ] Deciding a non-`PENDING` request returns 409.
- [ ] An `EMPLOYEE`, or a manager who is not the requester's manager, receives 403.
- [ ] A manager cannot approve or reject their own request (403).

### T09: Manager decisions UI

**Depends on:** T07, T08

**Scope:** Angular "Pending approvals" view for managers with approve and
reject (with comment) actions.

**Acceptance criteria:**

- [ ] View is reachable only by `MANAGER` users (route guard unit test).
- [ ] Reject dialog requires a comment before the action is enabled (unit test).
- [ ] After a decision, the request is removed from the pending list (unit test with mocked HTTP).

### T10: Leave history and status

**Depends on:** T08, T09

**Scope:** `GET /api/leave-requests/history` with filters (status, date
range) and pagination. Employees see their own history; managers see their
own plus their direct reports'. Angular history table with status badges
and decision comments.

**Acceptance criteria:**

- [ ] History returns requests ordered by `created_at` descending with page/size parameters (integration test).
- [ ] Status and date-range filters return only matching requests (integration test).
- [ ] An employee never receives another employee's requests (integration test).
- [ ] The history table renders status and decision comment for each row (unit test).

### T11: Dashboard

**Depends on:** T10

**Scope:** `GET /api/dashboard` returning role-specific summary data, plus an
Angular dashboard as the post-login landing page. Employee: counts of
pending/approved/rejected requests and approved days taken this year.
Manager: additionally, number of pending approvals and team members on leave
today.

**Acceptance criteria:**

- [ ] Employee dashboard counts match the seeded/test data exactly (integration test).
- [ ] Manager dashboard includes `pendingApprovals` and `onLeaveToday`; employee response omits them (integration test).
- [ ] Dashboard is the default route after login (unit test).

### T12: Test suite and coverage gate

**Depends on:** T11

**Scope:** Consolidate backend unit and Testcontainers integration tests and
frontend unit tests; add coverage reporting (JaCoCo, Karma/Jest coverage)
with minimum thresholds.

**Acceptance criteria:**

- [ ] `cd backend && ./mvnw -B verify` runs unit and integration tests and fails if line coverage is below 80%.
- [ ] `cd frontend && npm test -- --watch=false --code-coverage` passes and fails if line coverage is below 70%.
- [ ] Every API endpoint from T03–T11 has at least one success-path and one authorization-failure test.
- [ ] Tests use only synthetic data and require no external services other than Testcontainers.

### T13: Docker Compose local environment

**Depends on:** T11

**Scope:** `docker-compose.yml` with `db` (PostgreSQL), `backend`, and
`frontend` services, multi-stage Dockerfiles, health checks, and
configuration via `.env` (copied from `.env.example`). Local use only; no
deployment or infrastructure changes.

**Acceptance criteria:**

- [ ] `docker compose config` validates without errors.
- [ ] `docker compose up -d --build` brings all services to `healthy` within 3 minutes.
- [ ] With the stack running, demo login and the dashboard work via the frontend URL documented in the README.
- [ ] No secrets are hard-coded in `docker-compose.yml` or Dockerfiles; values come from `.env` with demo-only defaults in `.env.example`.

### T14: Application CI workflow

**Depends on:** T12, T13

**Scope:** Extend GitHub Actions to build and test backend and frontend,
run coverage gates, and validate `docker compose config` on every pull
request. No deployment jobs.

**Acceptance criteria:**

- [ ] Workflow runs on `pull_request` and executes the T12 test commands and `docker compose config`.
- [ ] A failing test or coverage below threshold fails the workflow.
- [ ] Workflow uses no repository secrets and contains no deploy steps.
- [ ] The plan verification and `git diff --check` jobs continue to pass.
