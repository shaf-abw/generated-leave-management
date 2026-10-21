package com.example.leave;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class HistoryIT extends IntegrationTestBase {

    private static final LocalDate BASE = LocalDate.of(2030, 3, 1);

    @Test
    void historyIsOrderedNewestFirstAndPaginated() throws Exception {
        long first = insertLeave(ALEX, BASE, BASE, "APPROVED", "2026-01-01T09:00:00Z");
        long second = insertLeave(ALEX, BASE.plusDays(2), BASE.plusDays(2), "REJECTED", "2026-01-02T09:00:00Z");
        long third = insertLeave(ALEX, BASE.plusDays(4), BASE.plusDays(4), "PENDING", "2026-01-03T09:00:00Z");
        RequestPostProcessor alex = as(ALEX);

        mvc.perform(get("/api/leave-requests/history").param("page", "0").param("size", "2").with(alex))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(third))
                .andExpect(jsonPath("$.content[1].id").value(second))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2));
        mvc.perform(get("/api/leave-requests/history").param("page", "1").param("size", "2").with(alex))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(first));
    }

    @Test
    void statusAndDateRangeFiltersReturnOnlyMatches() throws Exception {
        insertLeave(ALEX, BASE, BASE.plusDays(1), "APPROVED");
        long inRange = insertLeave(ALEX, BASE.plusDays(10), BASE.plusDays(12), "APPROVED");
        insertLeave(ALEX, BASE.plusDays(11), BASE.plusDays(11), "REJECTED");
        insertLeave(ALEX, BASE.plusDays(30), BASE.plusDays(31), "APPROVED");
        RequestPostProcessor alex = as(ALEX);

        mvc.perform(get("/api/leave-requests/history").param("status", "APPROVED").with(alex))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].status", everyItem(is("APPROVED"))));
        mvc.perform(get("/api/leave-requests/history").with(alex)
                        .param("from", BASE.plusDays(12).toString()).param("to", BASE.plusDays(20).toString()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(inRange));
        mvc.perform(get("/api/leave-requests/history").with(alex).param("status", "REJECTED")
                        .param("from", BASE.plusDays(5).toString()).param("to", BASE.plusDays(15).toString()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("REJECTED"));
    }

    @Test
    void employeeNeverReceivesOtherEmployeesRequests() throws Exception {
        insertLeave(ALEX, BASE, BASE, "PENDING");
        insertLeave(SAM, BASE, BASE, "APPROVED");
        insertLeave(JORDAN, BASE, BASE, "REJECTED");
        insertLeave(MORGAN, BASE, BASE, "APPROVED");
        mvc.perform(get("/api/leave-requests/history").with(as(ALEX)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].employeeId").value(employeeId(ALEX)));
    }

    @Test
    void managerSeesOwnAndDirectReportsHistory() throws Exception {
        insertLeave(ALEX, BASE, BASE, "PENDING");
        insertLeave(SAM, BASE, BASE, "APPROVED");
        insertLeave(RILEY, BASE, BASE, "APPROVED");
        insertLeave(JORDAN, BASE, BASE, "REJECTED");
        mvc.perform(get("/api/leave-requests/history").with(as(RILEY)))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/leave-requests/history").with(as(MORGAN)))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void invalidParametersReturn400() throws Exception {
        RequestPostProcessor alex = as(ALEX);
        mvc.perform(get("/api/leave-requests/history").param("size", "0").with(alex))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/leave-requests/history").param("size", "101").with(alex))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/leave-requests/history").param("page", "-1").with(alex))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/leave-requests/history").param("from", "2030-02-01").param("to", "2030-01-01")
                .with(alex)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/leave-requests/history").param("status", "BOGUS").with(alex))
                .andExpect(status().isBadRequest());
    }
}
