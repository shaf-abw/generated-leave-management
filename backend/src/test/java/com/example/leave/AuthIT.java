package com.example.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@ExtendWith(OutputCaptureExtension.class)
class AuthIT extends IntegrationTestBase {

    @Test
    void healthEndpointIsPublicAndUp() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void loginWithValidCredentialsReturnsCurrentUser() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("username", ALEX, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ALEX))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));
    }

    @Test
    void loginIsCaseInsensitiveOnUsername() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("username", " Alex.Example@Example.com ", "password", PASSWORD))))
                .andExpect(status().isOk());
    }

    @Test
    void loginWithInvalidCredentialsReturns401(CapturedOutput output) throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("username", ALEX, "password", "wrong-password"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("username", "nobody@example.com", "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
        assertThat(output.getAll()).doesNotContain(PASSWORD).doesNotContain("wrong-password");
    }

    @Test
    void loginWithMissingFieldsReturns400() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }

    @Test
    void deactivatedEmployeeCannotLogIn() throws Exception {
        jdbc.update("UPDATE employee SET active = FALSE WHERE email = ?", SAM);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("username", SAM, "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsIdNameAndRole() throws Exception {
        MockHttpSession session = login(MORGAN);
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(employeeId(MORGAN)))
                .andExpect(jsonPath("$.name").value("Morgan Manager"))
                .andExpect(jsonPath("$.role").value("MANAGER"));
    }

    @Test
    void logoutInvalidatesTheSession() throws Exception {
        MvcResult login = loginResult(ALEX);
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        Cookie xsrf = login.getResponse().getCookie("XSRF-TOKEN");
        mvc.perform(post("/api/auth/logout").session(session).cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void managerOnlyEndpointReturns403ForEmployee() throws Exception {
        mvc.perform(get("/api/leave-requests/pending").with(as(ALEX))).andExpect(status().isForbidden());
        mvc.perform(get("/api/leave-requests/pending").with(as(MORGAN))).andExpect(status().isOk());
    }

    @Test
    void csrfTokenCookieIsIssuedAndRequiredForStateChangingRequests() throws Exception {
        MockHttpSession session = login(ALEX);
        MvcResult me = mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andReturn();
        Cookie xsrf = me.getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();
        assertThat(xsrf.isHttpOnly()).isFalse();

        mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(session).cookie(xsrf).header("X-XSRF-TOKEN", "forged"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(session).cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isNoContent());
    }

    @Test
    void unauthenticatedStateChangingRequestWithoutCsrfReturns401() throws Exception {
        mvc.perform(post("/api/leave-requests").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    static Stream<MockHttpServletRequestBuilder> protectedEndpoints() {
        return Stream.of(
                get("/api/auth/me"),
                post("/api/auth/logout"),
                get("/api/employees"),
                get("/api/employees/1"),
                post("/api/employees").contentType(MediaType.APPLICATION_JSON).content("{}"),
                put("/api/employees/1").contentType(MediaType.APPLICATION_JSON).content("{}"),
                delete("/api/employees/1"),
                post("/api/leave-requests").contentType(MediaType.APPLICATION_JSON).content("{}"),
                get("/api/leave-requests/mine"),
                post("/api/leave-requests/1/cancel"),
                get("/api/leave-requests/pending"),
                post("/api/leave-requests/1/approve"),
                post("/api/leave-requests/1/reject").contentType(MediaType.APPLICATION_JSON).content("{}"),
                get("/api/leave-requests/history"),
                get("/api/dashboard"),
                get("/api/unknown"));
    }

    @ParameterizedTest
    @MethodSource("protectedEndpoints")
    void apiEndpointsRequireAuthentication(MockHttpServletRequestBuilder request) throws Exception {
        mvc.perform(request).andExpect(status().isUnauthorized());
    }
}
