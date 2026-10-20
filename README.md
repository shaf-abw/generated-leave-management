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

Repository skeleton only. The platform API and approval gate are being established before generated application tasks are dispatched.
