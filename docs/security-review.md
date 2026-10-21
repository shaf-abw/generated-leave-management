# Security Review — Leave Management MVP

| Item | Value |
|---|---|
| Repository | `shaf-abw/generated-leave-management` |
| Reviewed commit | `3b3a3c601622b446c63ff4b95d731a5d07a25019` (merge of PR #2, `main`) |
| Review type | Independent, read-only static review plus local tooling runs. Only synthetic/demo data was used. |
| Scope | Authentication and session lifecycle, CSRF, role and direct-report authorization, employee access, leave validation and concurrent decisions, migration/seed credentials, container exposure, CI and dependencies |
| Out of scope | Production or deployed environments, real employee data, dynamic penetration testing against a running deployment |

No application source or configuration was changed. This file is the only change in the review PR.
Line numbers refer to the reviewed commit. Credentials are intentionally not reproduced in this report.

## Summary

| ID | Severity | Title |
|---|---|---|
| F-01 | High (if deployed beyond local demo) | Demo accounts with a shared, publicly documented password are seeded by a versioned migration that runs in every environment |
| F-02 | Medium | Sessions survive employee deactivation and role changes (stale authorities in the session) |
| F-03 | Medium | Any manager can edit any employee, including promoting users and re-parenting reports, which bypasses the direct-report approval boundary |
| F-04 | Medium | Leave decisions and cancellations have no concurrency control (lost update / double decision) |
| F-05 | Medium | No brute-force protection on `POST /api/auth/login` |
| F-06 | Low | Session cookie lacks `Secure`; no HSTS / TLS termination guidance |
| F-07 | Low | Login endpoint is exempt from CSRF (login CSRF) |
| F-08 | Low | nginx serves the SPA without security headers (CSP, frame protection, etc.) and with version disclosure |
| F-09 | Low | Frontend port is published on all host interfaces |
| F-10 | Low | CI and container supply-chain hardening gaps (tag-pinned actions and images, no dependency scanning) |
| F-11 | Low | Known high-severity advisories in frontend dev/build tooling |
| F-12 | Low | Leave date range is unbounded and "today" depends on the JVM default time zone |
| F-13 | Informational | 403 vs 404 responses allow leave-request ID enumeration |
| F-14 | Informational | Empty default datasource password |

## Findings

### F-01 — Demo accounts with a shared, documented password seeded in every environment (High, conditional)

**Evidence**
- `backend/src/main/resources/db/migration/V2__seed_demo_data.sql:1-3` documents a single shared demo password in plain text in a comment.
- `backend/src/main/resources/db/migration/V2__seed_demo_data.sql:20-22` inserts an `app_user` for every seeded employee (including two `MANAGER` accounts) with the same BCrypt hash.
- `backend/src/main/resources/application.yml:12-14` enables Flyway unconditionally with `classpath:db/migration`, so `V2` runs in any environment the jar is deployed to; there is no profile or location split for demo data.

**Impact** Any non-local deployment built from this commit starts with known manager and employee logins (usernames are the `@example.com` seed emails and the password is in the repository). An attacker can sign in as a manager, view all employees, approve/reject requests, and modify employee records (see F-03). Because the data is in a versioned migration, removing it later requires a new migration rather than a config change.

**Remediation**
- Move demo seed data out of `db/migration` into a separate location (for example `db/demo`) and add it to `spring.flyway.locations` only under a `demo`/`local` profile.
- Remove the plaintext password from the SQL comment; document demo credentials only in local-dev docs, or generate them at startup for local profiles.
- For real environments, provision the first administrator out-of-band and force a password change on first login.

**Verification** Start the app without the demo profile and confirm `select count(*) from app_user` is `0`; with the demo profile, confirm the demo users exist. Add a `MigrationIT` assertion for both cases.

### F-02 — Sessions survive deactivation and role changes (Medium)

**Evidence**
- `backend/src/main/java/com/example/leave/auth/CurrentUser.java:18-21,44-47` stores `role` and `enabled` in the principal that is serialized into the HTTP session; authorities are derived from that snapshot.
- `backend/src/main/java/com/example/leave/config/SecurityConfig.java:66-69` uses a plain `HttpSessionSecurityContextRepository`; nothing reloads the user per request.
- `backend/src/main/java/com/example/leave/employee/EmployeeService.java:72-79` (`deactivate`) and `:51-70` (`update`, which can change `role` and `active`) do not invalidate the affected user's sessions.
- `backend/src/main/java/com/example/leave/auth/AuthController.java:73-78` (`/me`) returns the employee without checking `active`.
- `DaoAuthenticationProvider` only checks `isEnabled()` at login, so a deactivated user is blocked from *new* logins but not from an existing session.

**Impact** A deactivated employee keeps full API access until the session expires (default 30 minutes idle, renewed on each request, so potentially indefinitely while active). A demoted manager keeps `ROLE_MANAGER` for `@PreAuthorize("hasRole('MANAGER')")` endpoints (`LeaveRequestController.java:47-65`, `EmployeeController.java:39-57`) and can continue to approve/reject or edit employees.

**Remediation**
- Re-validate the principal per request (e.g. a filter that loads `employee.active`/`role` by `employeeId` and clears the context on mismatch), or
- Register a `SessionRegistry` (`sessionManagement().maximumSessions(...)`) and expire all sessions of an employee on deactivate/role change, and/or
- Add an explicit `active` check in `LeaveRequestService` and `EmployeeService` mutating paths.
- Consider an absolute session lifetime in addition to idle timeout.

**Verification** Integration test: log in as a synthetic employee, deactivate them with a manager session, then call `GET /api/auth/me` and `POST /api/leave-requests` with the old session — expect `401`. Same for a manager demoted to `EMPLOYEE` calling `/api/leave-requests/pending` — expect `403`/`401`.

### F-03 — Any manager can edit any employee and re-parent reports (Medium)

**Evidence**
- `backend/src/main/java/com/example/leave/employee/EmployeeController.java:39-57` protects create/update/delete only with `hasRole('MANAGER')`; `update` does not receive the caller.
- `backend/src/main/java/com/example/leave/employee/EmployeeService.java:51-70` lets the caller set any employee's `role`, `managerId`, `email` and `active` with no ownership check; this includes the caller's own record (self role/manager changes) and other managers.
- `backend/src/main/java/com/example/leave/leave/LeaveRequestService.java:79-95` authorizes decisions solely by `requester.getManager().getId() == caller`, which the caller can change via the endpoint above.
- `EmployeeService.java:72-79` lets any manager deactivate any other manager, including one with active direct reports (no `existsByManagerIdAndActiveTrue` check, unlike `update` at `:58-61`).
- `EmployeeService.java:21-27` returns every employee (with email and reporting line) to every manager.

**Impact** The direct-report boundary (plan T08: "a manager who is not the requester's manager receives 403") is enforceable only against honest managers. Manager A can `PUT /api/employees/{id}` to set `managerId = A` for another manager's report and then approve that report's pending leave, or promote an arbitrary employee to `MANAGER`. A manager can also lock out peers via deactivation. Changes are not audited.

**Remediation**
- Decide the intended admin model; if managers are not meant to be HR admins, introduce a separate `ADMIN`/`HR` role for create/update/delete and role/manager assignment.
- At minimum: forbid self-modification of `role`/`managerId`/`active`; restrict managers to editing their own direct reports; block deactivation of managers that still have active reports.
- Record an audit trail (who changed `role`, `manager_id`, `active`, and who decided each request).

**Verification** Integration tests with two synthetic managers: manager B `PUT`s manager A's report to report to B → expect `403`; manager promotes self/other → expect `403`; decision on a re-parented request is refused.

### F-04 — No concurrency control on decisions and cancellations (Medium)

**Evidence**
- `backend/src/main/java/com/example/leave/leave/LeaveRequestService.java:79-95` (`decide`) and `:60-71` (`cancel`) do read-check-write (`status == PENDING` then set) using `requests.findById` without a lock.
- `backend/src/main/java/com/example/leave/leave/LeaveRequest.java` has no `@Version` field; the only pessimistic lock in the codebase is `EmployeeRepository.findByIdForUpdate` (`EmployeeRepository.java:21-23`), used for submission only.
- Postgres default isolation is `READ COMMITTED`, so two transactions can both observe `PENDING`.
- No concurrency test exists under `backend/src/test` (no thread/executor usage).

**Impact** Concurrent approve + reject (e.g. double-click, two tabs) or approve + employee cancel both succeed; the last write wins and the response to one actor is wrong (e.g. manager sees "approved", final state is "cancelled", or `decided_by`/comment come from a different decision). This undermines integrity of the decision record.

**Remediation** Add `@Version private long version;` to `LeaveRequest` (plus a Flyway migration adding the column) and map `OptimisticLockingFailureException` to `409`, or load the request with `@Lock(PESSIMISTIC_WRITE)` in `decide`/`cancel`. Alternatively use a conditional update (`update ... set status = :new where id = :id and status = 'PENDING'`) and check the affected row count.

**Verification** Integration test that fires approve and reject (and approve and cancel) in parallel against the same synthetic request; exactly one must return `200` and the other `409`, and the persisted state must match the successful response.

### F-05 — No brute-force protection on login (Medium)

**Evidence** `backend/src/main/java/com/example/leave/auth/AuthController.java:44-64` authenticates every request without throttling, lockout, or delay; `SecurityConfig.java:53` permits it anonymously; nginx (`frontend/nginx.conf:17-22`) has no `limit_req`.

**Impact** Online password guessing/credential stuffing is unthrottled, which is especially relevant combined with F-01 and with no password policy for accounts.

**Remediation** Add rate limiting per IP and per username (nginx `limit_req_zone` on `/api/auth/login` and/or an application-level limiter such as Bucket4j), progressive delay or temporary lockout after repeated failures, and logging of failed attempts.

**Verification** Script >N failed logins for a synthetic user and confirm `429`/lockout after the threshold while a different user is unaffected.

### F-06 — Session cookie lacks `Secure`; no HSTS / TLS guidance (Low)

**Evidence** `backend/src/main/resources/application.yml:18-22` sets `http-only: true` and `same-site: lax` but not `secure: true`; nginx listens on plain HTTP only (`frontend/nginx.conf:2`); the backend does not configure `server.forward-headers-strategy`, so it cannot tell it is behind TLS.

**Impact** If deployed behind HTTP or a misconfigured proxy, `JSESSIONID` and `XSRF-TOKEN` can be sent in clear text and hijacked.

**Remediation** Set `server.servlet.session.cookie.secure=true` for non-local profiles, terminate TLS at the proxy, add `Strict-Transport-Security`, and set `server.forward-headers-strategy=native` (or `framework`) so the app honors `X-Forwarded-Proto`.

**Verification** Inspect `Set-Cookie` on login in a non-local profile for `Secure; HttpOnly; SameSite=Lax`.

### F-07 — Login is exempt from CSRF (Low)

**Evidence** `backend/src/main/java/com/example/leave/config/SecurityConfig.java:47-50` (`ignoringRequestMatchers("/api/auth/login")`).

**Impact** A third-party site can submit a cross-site login (login CSRF), signing the victim into an attacker-controlled account so that data the victim enters (leave reasons) is recorded under the attacker's account. Impact is limited because the endpoint requires a JSON body (`@RequestBody`), which forces a CORS preflight for `application/json` from a browser; it is still defense-in-depth.

**Remediation** Require CSRF on login as well: have the SPA call a cheap endpoint (e.g. `GET /api/auth/csrf` or `/api/auth/me`) to obtain the `XSRF-TOKEN` cookie before posting credentials, and remove the exemption.

**Verification** `POST /api/auth/login` without `X-XSRF-TOKEN` returns `403`; the Angular login still works end to end.

Note: other CSRF controls look correct — `CookieCsrfTokenRepository.withHttpOnlyFalse()` with `SpaCsrfTokenRequestHandler` (`SpaCsrfTokenRequestHandler.java:21-31`) and Angular `withXsrfConfiguration` (`frontend/src/app/app.config.ts:17-20`), and logout is a CSRF-protected `POST` (`AuthController.java:66-71`).

### F-08 — nginx SPA responses lack security headers (Low)

**Evidence** `frontend/nginx.conf:1-27` sets no `add_header` directives and leaves `server_tokens` at its default. Spring Security's default headers apply only to responses proxied from the backend, not to `index.html` or static assets served by nginx.

**Impact** The SPA can be framed (clickjacking on approve/reject buttons), has no CSP to limit the blast radius of any future XSS, and discloses the nginx version.

**Remediation** Add `server_tokens off;` and headers such as `Content-Security-Policy: default-src 'self'; frame-ancestors 'none'`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: same-origin`, and (with TLS) `Strict-Transport-Security`; use `always` so they apply to error pages.

**Verification** `curl -I http://localhost:<port>/` shows the headers and no version in `Server`.

### F-09 — Frontend published on all host interfaces (Low)

**Evidence** `docker-compose.yml:32-33` (`"${FRONTEND_PORT:-4200}:80"`) binds `0.0.0.0`. Positively, `db` and `backend` publish no ports (`docker-compose.yml:3-28`).

**Impact** On a developer laptop or shared VM, the app (with the F-01 demo accounts) is reachable from the local network, not just localhost.

**Remediation** Bind to loopback by default: `"127.0.0.1:${FRONTEND_PORT:-4200}:80"`, and document how to opt in to wider exposure.

**Verification** `docker compose config` shows `host_ip: 127.0.0.1`; the port is not reachable from another host.

### F-10 — CI / container supply-chain hardening gaps (Low)

**Evidence**
- `.github/workflows/app-ci.yml:18,19,28,40,41` and `.github/workflows/plan-check.yml:15,16` reference third-party actions by mutable major tags (`@v4`, `@v5`) rather than commit SHAs.
- `backend/Dockerfile:1,12`, `frontend/Dockerfile:1,8`, and `docker-compose.yml:4` use mutable image tags without digests.
- No dependency or image scanning step (no Dependabot config, `npm audit`, OWASP dependency-check, or Trivy) in either workflow.
- Positives: workflows set `permissions: contents: read` (`app-ci.yml:8-9`, `plan-check.yml:8-9`); triggers are `pull_request`, not `pull_request_target`; `npm ci` uses a lockfile; the backend runtime image runs as non-root (`backend/Dockerfile:13,16`).

**Impact** A compromised upstream tag could execute in CI or ship in images; vulnerable dependencies are not surfaced automatically.

**Remediation** Pin actions to full commit SHAs (with Dependabot `github-actions` updates), pin base images by digest, add Dependabot for `maven`, `npm`, and `docker`, and add a scanning job (e.g. `npm audit --omit=dev --audit-level=high`, OWASP dependency-check or Trivy on built images).

**Verification** Workflow files reference SHAs; a scanning job runs on PRs and fails on high-severity production findings.

### F-11 — Known advisories in frontend dev/build tooling (Low)

**Evidence** Running `npm audit` against `frontend/package-lock.json` at the reviewed commit reported **17 high-severity** advisories, all in dev/build dependencies (for example `@angular/cli` via `@modelcontextprotocol/sdk`, `@angular/build` via `vite`, `karma` via `chokidar`/`braces`, and `typescript-eslint`). `npm audit --omit=dev` reported **0 vulnerabilities**, so the shipped bundle is not affected.

**Impact** Risk is limited to developer machines and CI runners (for example the Vite dev server when running `ng serve`).

**Remediation** Upgrade within the supported Angular 20 line where fixes exist, track the rest via Dependabot, and avoid exposing `ng serve` beyond localhost.

**Verification** Re-run `npm audit` and confirm the high-severity count drops; keep `npm audit --omit=dev` at 0.

### F-12 — Leave date range unbounded; "today" depends on JVM time zone (Low)

**Evidence**
- `backend/src/main/java/com/example/leave/leave/LeaveRequestService.java:36-42` checks only `end >= start` and `start >= today`; `LeaveRequestCreate.java:7-12` has no maximum range or horizon.
- `backend/src/main/java/com/example/leave/config/ClockConfig.java:10-13` uses `Clock.systemDefaultZone()`, so "past" depends on the container's zone (UTC in the Alpine JRE image).
- Positives: overlap checks are serialized per employee via `findByIdForUpdate` (`LeaveRequestService.java:43-47`, `EmployeeRepository.java:21-23`); DB `CHECK` constraints back the enum and date-order rules (`V1__create_schema.sql:34-36`); type and status are enum-bound; reason/comment lengths are limited.

**Impact** Users can submit requests spanning years or decades in the future (e.g. to block future periods or skew dashboard counts); requests near midnight may be rejected or accepted inconsistently for users outside UTC.

**Remediation** Enforce a maximum duration and booking horizon (configurable), and define a business time zone explicitly (e.g. `Clock.system(ZoneId.of(...))`) or accept the client's zone with bounds.

**Verification** Unit/integration tests that reject ranges beyond the configured maximum and confirm boundary behavior at the configured zone's midnight.

### F-13 — Leave-request ID enumeration via 403 vs 404 (Informational)

**Evidence** `LeaveRequestService.java:141-143` returns `404 "Leave request {id} not found"` for missing IDs while `:62-65` and `:83-88` return `403` for existing requests owned by others.

**Impact** An authenticated user can learn which IDs exist and roughly how many requests exist. No request contents are disclosed.

**Remediation** Return `404` for requests the caller is not allowed to act on, or scope the lookup query by caller.

### F-14 — Empty default datasource password (Informational)

**Evidence** `backend/src/main/resources/application.yml:7` (`${DB_PASSWORD:}`). Compose requires the variable (`docker-compose.yml:8,25`), and `.env.example:5` contains only a clearly marked demo placeholder; `.env` is git-ignored (`.gitignore:1`).

**Impact** Outside Compose, a missing variable silently falls back to an empty password instead of failing fast, which can mask misconfiguration against a trust-auth database.

**Remediation** Remove the empty default (`${DB_PASSWORD}`) so startup fails when it is unset in non-local profiles.

## Areas reviewed with no finding

- **Session fixation:** session ID is rotated on login (`AuthController.java:55-57`); the context is saved explicitly via `SecurityContextRepository`.
- **Password storage:** BCrypt via `DaoAuthenticationProvider` (`SecurityConfig.java:71-82`); the password hash is `transient` and erased after authentication (`CurrentUser.java:22,64-67`); `LoginRequest.toString()` masks the password (`LoginRequest.java:8-11`); generic error on failed login (`AuthController.java:52-54`).
- **Default-deny routing:** `/api/**` requires authentication and everything else not explicitly allowed is `denyAll` (`SecurityConfig.java:51-56`); only `health` is exposed by actuator (`application.yml:24-32`).
- **Employee self-access:** non-managers see only their own employee record (`EmployeeService.java:21-35`).
- **History / pending / dashboard scoping:** queries are scoped to the caller and (for managers) direct reports (`LeaveRequestService.java:73-77,97-139`; `DashboardService.java:23-44`); page size is bounded (`LeaveRequestService.java:22,100-105`).
- **Self-approval:** blocked (`LeaveRequestService.java:83-85`).
- **SQL injection:** all queries use JPQL parameters or the Criteria API.
- **Frontend:** no `innerHTML`, `bypassSecurityTrust*`, or token storage in `localStorage`/`sessionStorage` found in `frontend/src`; session is cookie-based with `withCredentials`.

## Verification notes (checks actually run for this review)

All checks were run locally against a clean worktree of commit `3b3a3c601622b446c63ff4b95d731a5d07a25019` using synthetic/demo data only.

| Check | Command | Result |
|---|---|---|
| Frontend install | `npm ci` (Node 22 container) | Passed |
| Frontend lint | `npm run lint` | Passed ("All files pass linting.") |
| Frontend production build | `npm run build` | Passed |
| Frontend production dependency audit | `npm audit --omit=dev` | 0 vulnerabilities |
| Frontend full dependency audit | `npm audit` | 17 high (dev/build tooling only), see F-11 |
| Backend `./mvnw -B verify` | Temurin 21 container | **Not completed** — Maven Central returned HTTP 429 (rate limited) from the review environment, so the Maven wrapper could not download Maven. Backend tests are exercised by the repository's required `Application CI` workflow on the review PR. |
| Frontend Karma unit tests | — | Not run locally (no headless Chrome in the review environment); exercised by the `Application CI` workflow on the review PR. |

Findings F-01 to F-14 come from static review of the code at the reviewed commit. They were not exploited against any running or deployed instance.
