package com.example.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class ManagerDecisionIT extends IntegrationTestBase {

    private static final LocalDate START = LocalDate.now().plusDays(7);

    @Test
    void managerSeesOnlyPendingRequestsOfDirectReports() throws Exception {
        long alexPending = insertLeave(ALEX, START, START, "PENDING");
        long rileyPending = insertLeave(RILEY, START, START, "PENDING");
        insertLeave(SAM, START, START, "APPROVED");
        long jordanPending = insertLeave(JORDAN, START, START, "PENDING");
        insertLeave(MORGAN, START, START, "PENDING");

        mvc.perform(get("/api/leave-requests/pending").with(as(MORGAN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(alexPending))
                .andExpect(jsonPath("$[1].id").value(rileyPending));
        mvc.perform(get("/api/leave-requests/pending").with(as(RILEY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(jordanPending));
    }

    @Test
    void approveRecordsDecision() throws Exception {
        long id = insertLeave(ALEX, START, START, "PENDING");
        mvc.perform(post("/api/leave-requests/" + id + "/approve").with(as(MORGAN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedById").value(employeeId(MORGAN)))
                .andExpect(jsonPath("$.decidedByName").value("Morgan Manager"))
                .andExpect(jsonPath("$.decidedAt").isNotEmpty());
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, decided_by, decided_at FROM leave_request WHERE id = ?", id);
        assertThat(row.get("status")).isEqualTo("APPROVED");
        assertThat(((Number) row.get("decided_by")).longValue()).isEqualTo(employeeId(MORGAN));
        assertThat(row.get("decided_at")).isNotNull();
    }

    @Test
    void approveAcceptsOptionalComment() throws Exception {
        long id = insertLeave(ALEX, START, START, "PENDING");
        mvc.perform(post("/api/leave-requests/" + id + "/approve").with(as(MORGAN))
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("comment", "Enjoy"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisionComment").value("Enjoy"));
    }

    @Test
    void rejectRecordsDecisionAndComment() throws Exception {
        long id = insertLeave(JORDAN, START, START, "PENDING");
        mvc.perform(post("/api/leave-requests/" + id + "/reject").with(as(RILEY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("comment", "Team offsite that week"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decisionComment").value("Team offsite that week"))
                .andExpect(jsonPath("$.decidedById").value(employeeId(RILEY)))
                .andExpect(jsonPath("$.decidedAt").isNotEmpty());
    }

    @Test
    void rejectWithoutCommentReturns400() throws Exception {
        long id = insertLeave(ALEX, START, START, "PENDING");
        RequestPostProcessor morgan = as(MORGAN);
        mvc.perform(post("/api/leave-requests/" + id + "/reject").with(morgan)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("comment", "  "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("comment"));
        mvc.perform(post("/api/leave-requests/" + id + "/reject").with(morgan))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT status FROM leave_request WHERE id = ?", String.class, id))
                .isEqualTo("PENDING");
    }

    @Test
    void decidingNonPendingRequestReturns409() throws Exception {
        RequestPostProcessor morgan = as(MORGAN);
        for (String status : new String[] {"APPROVED", "REJECTED", "CANCELLED"}) {
            long id = insertLeave(ALEX, START, START, status);
            mvc.perform(post("/api/leave-requests/" + id + "/approve").with(morgan))
                    .andExpect(status().isConflict());
            mvc.perform(post("/api/leave-requests/" + id + "/reject").with(morgan)
                            .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("comment", "No"))))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    void employeeOrOtherManagerReceives403() throws Exception {
        long id = insertLeave(ALEX, START, START, "PENDING");
        mvc.perform(post("/api/leave-requests/" + id + "/approve").with(as(SAM))).andExpect(status().isForbidden());
        mvc.perform(post("/api/leave-requests/" + id + "/approve").with(as(RILEY)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/leave-requests/" + id + "/reject").with(as(RILEY))
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("comment", "No"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/leave-requests/pending").with(as(SAM))).andExpect(status().isForbidden());
    }

    @Test
    void managerCannotDecideOwnRequest() throws Exception {
        long id = insertLeave(RILEY, START, START, "PENDING");
        RequestPostProcessor riley = as(RILEY);
        mvc.perform(post("/api/leave-requests/" + id + "/approve").with(riley)).andExpect(status().isForbidden());
        mvc.perform(post("/api/leave-requests/" + id + "/reject").with(riley)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("comment", "No"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void decidingUnknownRequestReturns404() throws Exception {
        mvc.perform(post("/api/leave-requests/999999/approve").with(as(MORGAN))).andExpect(status().isNotFound());
    }
}
