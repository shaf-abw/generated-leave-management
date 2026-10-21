package com.example.leave;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DashboardIT extends IntegrationTestBase {

    private static final LocalDate TODAY = LocalDate.now();
    private static final LocalDate YEAR_START = TODAY.withDayOfYear(1);

    @Test
    void employeeCountsMatchData() throws Exception {
        insertLeave(ALEX, TODAY.plusDays(30), TODAY.plusDays(31), "PENDING");
        insertLeave(ALEX, YEAR_START.plusDays(1), YEAR_START.plusDays(3), "APPROVED");
        insertLeave(ALEX, YEAR_START.minusDays(2), YEAR_START.plusDays(1), "APPROVED");
        insertLeave(ALEX, YEAR_START.minusYears(1), YEAR_START.minusYears(1).plusDays(4), "APPROVED");
        insertLeave(ALEX, YEAR_START.plusDays(10), YEAR_START.plusDays(10), "REJECTED");
        insertLeave(ALEX, YEAR_START.plusDays(12), YEAR_START.plusDays(12), "CANCELLED");
        insertLeave(SAM, YEAR_START.plusDays(1), YEAR_START.plusDays(5), "APPROVED");

        mvc.perform(get("/api/dashboard").with(as(ALEX)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.pendingCount").value(1))
                .andExpect(jsonPath("$.approvedCount").value(3))
                .andExpect(jsonPath("$.rejectedCount").value(1))
                .andExpect(jsonPath("$.approvedDaysThisYear").value(5))
                .andExpect(jsonPath("$.pendingApprovals").doesNotExist())
                .andExpect(jsonPath("$.onLeaveToday").doesNotExist());
    }

    @Test
    void managerDashboardIncludesTeamFigures() throws Exception {
        insertLeave(ALEX, TODAY.plusDays(5), TODAY.plusDays(6), "PENDING");
        insertLeave(SAM, TODAY.plusDays(8), TODAY.plusDays(8), "PENDING");
        insertLeave(JORDAN, TODAY.plusDays(8), TODAY.plusDays(8), "PENDING");
        insertLeave(ALEX, TODAY.minusDays(1), TODAY.plusDays(1), "APPROVED");
        insertLeave(SAM, TODAY, TODAY, "REJECTED");
        insertLeave(JORDAN, TODAY, TODAY, "APPROVED");
        insertLeave(MORGAN, TODAY.plusDays(20), TODAY.plusDays(20), "PENDING");

        mvc.perform(get("/api/dashboard").with(as(MORGAN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MANAGER"))
                .andExpect(jsonPath("$.pendingCount").value(1))
                .andExpect(jsonPath("$.pendingApprovals").value(2))
                .andExpect(jsonPath("$.onLeaveToday").value(1));
    }

    @Test
    void managerWithNoActivityGetsZeroes() throws Exception {
        mvc.perform(get("/api/dashboard").with(as(RILEY)))
                .andExpect(jsonPath("$.pendingApprovals").value(0))
                .andExpect(jsonPath("$.onLeaveToday").value(0))
                .andExpect(jsonPath("$.approvedDaysThisYear").value(0));
    }
}
