package com.example.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class MigrationIT extends IntegrationTestBase {

    @Autowired
    Flyway flyway;

    @Test
    void allMigrationsApplyCleanlyToAnEmptyDatabase() {
        MigrationInfo[] applied = flyway.info().applied();
        assertThat(applied).hasSizeGreaterThanOrEqualTo(2);
        assertThat(Arrays.stream(applied).map(MigrationInfo::getState)).allMatch(MigrationState.SUCCESS::equals);
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void statusIsConstrainedToKnownValues() {
        long alex = employeeId(ALEX);
        for (String status : List.of("PENDING", "APPROVED", "REJECTED", "CANCELLED")) {
            jdbc.update("INSERT INTO leave_request (employee_id, type, start_date, end_date, status) "
                    + "VALUES (?, 'ANNUAL', DATE '2030-01-01', DATE '2030-01-01', ?)", alex, status);
        }
        assertThatThrownBy(() -> jdbc.update("INSERT INTO leave_request (employee_id, type, start_date, end_date, "
                + "status) VALUES (?, 'ANNUAL', DATE '2030-01-01', DATE '2030-01-01', 'UNKNOWN')", alex))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("leave_request_status_check");
    }

    @Test
    void endDateBeforeStartDateIsRejected() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO leave_request (employee_id, type, start_date, end_date) "
                + "VALUES (?, 'ANNUAL', DATE '2030-01-02', DATE '2030-01-01')", employeeId(ALEX)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("leave_request_dates_check");
    }

    @Test
    void seedDataIsSyntheticAndIncludesManagersAndEmployees() {
        List<String> emails = jdbc.queryForList("SELECT email FROM employee", String.class);
        assertThat(emails).isNotEmpty().allMatch(email -> email.endsWith("@example.com"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM employee WHERE role = 'MANAGER'", Integer.class))
                .isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM employee WHERE role = 'EMPLOYEE'", Integer.class))
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void storedPasswordsAreBcryptHashes() {
        List<String> hashes = jdbc.queryForList("SELECT password_hash FROM app_user", String.class);
        assertThat(hashes).isNotEmpty()
                .allMatch(hash -> hash.matches("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$"))
                .noneMatch(hash -> hash.contains(PASSWORD));
    }
}
