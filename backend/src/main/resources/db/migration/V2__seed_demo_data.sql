-- DEMO DATA ONLY. Synthetic users for local development and tests.
-- Every account below uses the demo-only password "DemoPass123!" (BCrypt-hashed).
-- Never use these accounts or this password outside local/demo environments.

INSERT INTO employee (name, email, role, manager_id)
VALUES ('Morgan Manager', 'morgan.manager@example.com', 'MANAGER', NULL);

INSERT INTO employee (name, email, role, manager_id)
VALUES ('Riley Supervisor', 'riley.supervisor@example.com', 'MANAGER',
        (SELECT id FROM employee WHERE email = 'morgan.manager@example.com'));

INSERT INTO employee (name, email, role, manager_id)
VALUES ('Alex Example', 'alex.example@example.com', 'EMPLOYEE',
        (SELECT id FROM employee WHERE email = 'morgan.manager@example.com')),
       ('Sam Sample', 'sam.sample@example.com', 'EMPLOYEE',
        (SELECT id FROM employee WHERE email = 'morgan.manager@example.com')),
       ('Jordan Demo', 'jordan.demo@example.com', 'EMPLOYEE',
        (SELECT id FROM employee WHERE email = 'riley.supervisor@example.com'));

INSERT INTO app_user (employee_id, username, password_hash)
SELECT id, email, '$2a$10$rftxpHSni5BhmveIvziW1.dj0CL2ZMfsl.Z/6EaESLrQiRtU.wQZO'
FROM employee;
