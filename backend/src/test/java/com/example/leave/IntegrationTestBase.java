package com.example.leave;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTestBase {

    protected static final String PASSWORD = "DemoPass123!";
    protected static final String MORGAN = "morgan.manager@example.com";
    protected static final String RILEY = "riley.supervisor@example.com";
    protected static final String ALEX = "alex.example@example.com";
    protected static final String SAM = "sam.sample@example.com";
    protected static final String JORDAN = "jordan.demo@example.com";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected ObjectMapper json;

    @BeforeEach
    void resetData() {
        jdbc.update("DELETE FROM leave_request");
        jdbc.update("DELETE FROM app_user WHERE employee_id IN (SELECT id FROM employee WHERE email NOT IN (?,?,?,?,?))",
                MORGAN, RILEY, ALEX, SAM, JORDAN);
        jdbc.update("UPDATE employee SET manager_id = NULL WHERE email NOT IN (?,?,?,?,?)",
                MORGAN, RILEY, ALEX, SAM, JORDAN);
        jdbc.update("DELETE FROM employee WHERE email NOT IN (?,?,?,?,?)", MORGAN, RILEY, ALEX, SAM, JORDAN);
        jdbc.update("UPDATE employee SET active = TRUE");
    }

    protected MockHttpSession login(String username) throws Exception {
        return (MockHttpSession) loginResult(username).getRequest().getSession(false);
    }

    protected MvcResult loginResult(String username) throws Exception {
        return mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
    }

    /** Authenticated session plus the XSRF-TOKEN cookie and X-XSRF-TOKEN header, as the Angular client sends. */
    protected RequestPostProcessor as(String username) throws Exception {
        MvcResult login = loginResult(username);
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        Cookie xsrf = login.getResponse().getCookie("XSRF-TOKEN");
        return request -> {
            request.setSession(session);
            request.setCookies(xsrf);
            request.addHeader("X-XSRF-TOKEN", xsrf.getValue());
            return request;
        };
    }

    protected long employeeId(String email) {
        return jdbc.queryForObject("SELECT id FROM employee WHERE email = ?", Long.class, email);
    }

    protected long insertLeave(String email, LocalDate start, LocalDate end, String status) {
        return jdbc.queryForObject("""
                INSERT INTO leave_request (employee_id, type, start_date, end_date, status, created_at)
                VALUES (?, 'ANNUAL', ?, ?, ?, now()) RETURNING id
                """, Long.class, employeeId(email), start, end, status);
    }

    protected long insertLeave(String email, LocalDate start, LocalDate end, String status, String createdAt) {
        return jdbc.queryForObject("""
                INSERT INTO leave_request (employee_id, type, start_date, end_date, status, created_at)
                VALUES (?, 'ANNUAL', ?, ?, ?, CAST(? AS timestamptz)) RETURNING id
                """, Long.class, employeeId(email), start, end, status, createdAt);
    }

    protected String toJson(Object value) throws Exception {
        return json.writeValueAsString(value);
    }
}
