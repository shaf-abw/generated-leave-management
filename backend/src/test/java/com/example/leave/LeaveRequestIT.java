package com.example.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class LeaveRequestIT extends IntegrationTestBase {

    private static final LocalDate TODAY = LocalDate.now();

    private Map<String, Object> body(Object type, LocalDate start, LocalDate end) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", type);
        body.put("startDate", start);
        body.put("endDate", end);
        body.put("reason", "Family trip");
        return body;
    }

    @Test
    void validSubmissionIsPendingAndPersisted() throws Exception {
        String response = mvc.perform(post("/api/leave-requests").with(as(ALEX))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body("ANNUAL", TODAY.plusDays(10), TODAY.plusDays(12)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.days").value(3))
                .andExpect(jsonPath("$.employeeName").value("Alex Example"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(response).get("id").asLong();
        assertThat(jdbc.queryForObject("SELECT status FROM leave_request WHERE id = ?", String.class, id))
                .isEqualTo("PENDING");
    }

    @Test
    void startingTodayIsAllowed() throws Exception {
        mvc.perform(post("/api/leave-requests").with(as(ALEX)).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body("SICK", TODAY, TODAY))))
                .andExpect(status().isCreated());
    }

    @Test
    void invalidSubmissionsReturn400() throws Exception {
        RequestPostProcessor alex = as(ALEX);
        mvc.perform(post("/api/leave-requests").with(alex).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body("ANNUAL", TODAY.plusDays(5), TODAY.plusDays(4)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("endDate"));
        mvc.perform(post("/api/leave-requests").with(alex).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body("ANNUAL", TODAY.minusDays(1), TODAY.plusDays(1)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("startDate"));
        mvc.perform(post("/api/leave-requests").with(alex).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body(null, TODAY.plusDays(1), TODAY.plusDays(1)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("type"));
        mvc.perform(post("/api/leave-requests").with(alex).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body("HOLIDAY", TODAY.plusDays(1), TODAY.plusDays(1)))))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leave_request", Integer.class)).isZero();
    }

    @Test
    void overlappingPendingOrApprovedRequestReturns409() throws Exception {
        insertLeave(ALEX, TODAY.plusDays(10), TODAY.plusDays(15), "PENDING");
        insertLeave(ALEX, TODAY.plusDays(20), TODAY.plusDays(25), "APPROVED");
        insertLeave(ALEX, TODAY.plusDays(30), TODAY.plusDays(35), "REJECTED");
        insertLeave(SAM, TODAY.plusDays(40), TODAY.plusDays(45), "APPROVED");
        RequestPostProcessor alex = as(ALEX);
        mvc.perform(post("/api/leave-requests").with(alex).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body("ANNUAL", TODAY.plusDays(15), TODAY.plusDays(16)))))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/leave-requests").with(alex).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body("ANNUAL", TODAY.plusDays(18), TODAY.plusDays(21)))))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/leave-requests").with(alex).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body("ANNUAL", TODAY.plusDays(31), TODAY.plusDays(32)))))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/leave-requests").with(alex).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body("ANNUAL", TODAY.plusDays(41), TODAY.plusDays(42)))))
                .andExpect(status().isCreated());
    }

    @Test
    void mineListsOnlyOwnRequestsNewestFirst() throws Exception {
        long older = insertLeave(ALEX, TODAY.plusDays(1), TODAY.plusDays(1), "PENDING", "2025-01-01T00:00:00Z");
        long newer = insertLeave(ALEX, TODAY.plusDays(3), TODAY.plusDays(3), "APPROVED", "2025-02-01T00:00:00Z");
        insertLeave(SAM, TODAY.plusDays(5), TODAY.plusDays(5), "PENDING");
        mvc.perform(get("/api/leave-requests/mine").with(as(ALEX)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(newer))
                .andExpect(jsonPath("$[1].id").value(older));
    }

    @Test
    void cancellingPendingRequestSetsCancelled() throws Exception {
        long id = insertLeave(ALEX, TODAY.plusDays(1), TODAY.plusDays(2), "PENDING");
        mvc.perform(post("/api/leave-requests/" + id + "/cancel").with(as(ALEX)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancellingNonPendingRequestReturns409() throws Exception {
        RequestPostProcessor alex = as(ALEX);
        for (String status : new String[] {"APPROVED", "REJECTED", "CANCELLED"}) {
            long id = insertLeave(ALEX, TODAY.plusDays(1), TODAY.plusDays(2), status);
            mvc.perform(post("/api/leave-requests/" + id + "/cancel").with(alex)).andExpect(status().isConflict());
        }
    }

    @Test
    void cannotCancelAnotherEmployeesRequest() throws Exception {
        long id = insertLeave(SAM, TODAY.plusDays(1), TODAY.plusDays(2), "PENDING");
        mvc.perform(post("/api/leave-requests/" + id + "/cancel").with(as(ALEX))).andExpect(status().isForbidden());
        mvc.perform(post("/api/leave-requests/" + id + "/cancel").with(as(MORGAN))).andExpect(status().isForbidden());
    }

    @Test
    void cancellingUnknownRequestReturns404() throws Exception {
        mvc.perform(post("/api/leave-requests/999999/cancel").with(as(ALEX))).andExpect(status().isNotFound());
    }
}
