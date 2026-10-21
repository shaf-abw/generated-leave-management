package com.example.leave;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class EmployeeIT extends IntegrationTestBase {

    private Map<String, Object> payload(String name, String email, String role, Long managerId) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("email", email);
        body.put("role", role);
        body.put("managerId", managerId);
        return body;
    }

    private long create(RequestPostProcessor manager, Map<String, Object> body) throws Exception {
        String response = mvc.perform(post("/api/employees").with(manager)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    @Test
    void managerCreatesEmployee() throws Exception {
        mvc.perform(post("/api/employees").with(as(MORGAN)).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(payload("Casey Test", "Casey.Test@Example.com", "EMPLOYEE",
                                employeeId(MORGAN)))))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.email").value("casey.test@example.com"))
                .andExpect(jsonPath("$.managerName").value("Morgan Manager"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void managerReadsListAndSingleEmployee() throws Exception {
        RequestPostProcessor morgan = as(MORGAN);
        mvc.perform(get("/api/employees").with(morgan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[*].email", hasItem(JORDAN)));
        mvc.perform(get("/api/employees/" + employeeId(ALEX)).with(morgan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alex Example"));
    }

    @Test
    void employeeReadsOnlyOwnRecord() throws Exception {
        RequestPostProcessor alex = as(ALEX);
        mvc.perform(get("/api/employees").with(alex))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value(ALEX));
        mvc.perform(get("/api/employees/" + employeeId(ALEX)).with(alex)).andExpect(status().isOk());
        mvc.perform(get("/api/employees/" + employeeId(SAM)).with(alex)).andExpect(status().isForbidden());
    }

    @Test
    void managerUpdatesEmployee() throws Exception {
        RequestPostProcessor morgan = as(MORGAN);
        long id = create(morgan, payload("Casey Test", "casey.test@example.com", "EMPLOYEE", null));
        mvc.perform(put("/api/employees/" + id).with(morgan).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(payload("Casey Renamed", "casey.renamed@example.com", "EMPLOYEE",
                                employeeId(RILEY)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Casey Renamed"))
                .andExpect(jsonPath("$.managerId").value(employeeId(RILEY)));
        mvc.perform(get("/api/employees/" + id).with(morgan))
                .andExpect(jsonPath("$.email").value("casey.renamed@example.com"));
    }

    @Test
    void managerSoftDeletesEmployee() throws Exception {
        RequestPostProcessor morgan = as(MORGAN);
        long id = create(morgan, payload("Casey Test", "casey.test@example.com", "EMPLOYEE", null));
        mvc.perform(delete("/api/employees/" + id).with(morgan)).andExpect(status().isNoContent());
        mvc.perform(get("/api/employees/" + id).with(morgan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void managerCanReactivateEmployee() throws Exception {
        RequestPostProcessor morgan = as(MORGAN);
        Map<String, Object> body = payload("Casey Test", "casey.test@example.com", "EMPLOYEE", null);
        long id = create(morgan, body);
        mvc.perform(delete("/api/employees/" + id).with(morgan)).andExpect(status().isNoContent());
        body.put("active", true);
        mvc.perform(put("/api/employees/" + id).with(morgan).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void managerCannotDeactivateSelf() throws Exception {
        mvc.perform(delete("/api/employees/" + employeeId(MORGAN)).with(as(MORGAN)))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidPayloadReturnsFieldErrors() throws Exception {
        mvc.perform(post("/api/employees").with(as(MORGAN)).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(payload(" ", "not-an-email", null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("name")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("email")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("role")));
    }

    @Test
    void duplicateEmailReturns400() throws Exception {
        mvc.perform(post("/api/employees").with(as(MORGAN)).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(payload("Someone", ALEX.toUpperCase(), "EMPLOYEE", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
        mvc.perform(put("/api/employees/" + employeeId(SAM)).with(as(MORGAN)).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(payload("Sam Sample", ALEX, "EMPLOYEE", employeeId(MORGAN)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
    }

    @Test
    void invalidManagerReturns400() throws Exception {
        RequestPostProcessor morgan = as(MORGAN);
        mvc.perform(post("/api/employees").with(morgan).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(payload("Casey", "casey@example.com", "EMPLOYEE", employeeId(ALEX)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("managerId"));
        mvc.perform(post("/api/employees").with(morgan).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(payload("Casey", "casey@example.com", "EMPLOYEE", 999_999L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("managerId"));
        mvc.perform(put("/api/employees/" + employeeId(MORGAN)).with(morgan).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(payload("Morgan Manager", MORGAN, "MANAGER", employeeId(RILEY)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("managerId"));
    }

    @Test
    void managerWithReportsCannotBeDemoted() throws Exception {
        mvc.perform(put("/api/employees/" + employeeId(RILEY)).with(as(MORGAN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(payload("Riley Supervisor", RILEY, "EMPLOYEE", employeeId(MORGAN)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("role"));
    }

    @Test
    void employeeCannotCreateUpdateOrDelete() throws Exception {
        RequestPostProcessor alex = as(ALEX);
        String body = toJson(payload("Casey", "casey@example.com", "EMPLOYEE", null));
        mvc.perform(post("/api/employees").with(alex).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/employees/" + employeeId(ALEX)).with(alex).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/employees/" + employeeId(SAM)).with(alex)).andExpect(status().isForbidden());
    }

    @Test
    void unknownIdReturns404() throws Exception {
        RequestPostProcessor morgan = as(MORGAN);
        mvc.perform(get("/api/employees/999999").with(morgan)).andExpect(status().isNotFound());
        mvc.perform(put("/api/employees/999999").with(morgan).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(payload("Casey", "casey@example.com", "EMPLOYEE", null))))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/employees/999999").with(morgan)).andExpect(status().isNotFound());
        mvc.perform(get("/api/employees/not-a-number").with(morgan)).andExpect(status().isBadRequest());
    }
}
