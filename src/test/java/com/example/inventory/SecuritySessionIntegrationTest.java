package com.example.inventory;

import com.example.inventory.users.RoleName;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecuritySessionIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "sessions-password-123";

    @Test
    void changingOwnPasswordRevokesAccessAndEveryRefreshSession() throws Exception {
        createUser("password-user", PASSWORD, true, false, RoleName.SALES);
        String firstLogin = loginResponse("password-user", PASSWORD);
        String secondLogin = loginResponse("password-user", PASSWORD);
        String firstAccess = JsonPath.read(firstLogin, "$.accessToken");
        String firstRefresh = JsonPath.read(firstLogin, "$.refreshToken");
        String secondAccess = JsonPath.read(secondLogin, "$.accessToken");
        String secondRefresh = JsonPath.read(secondLogin, "$.refreshToken");
        String newPassword = "changed-password-456";

        mockMvc.perform(put("/api/v1/auth/password")
                        .header(AUTHORIZATION, "Bearer " + firstAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","newPassword":"%s"}
                                """.formatted(PASSWORD, newPassword)))
                .andExpect(status().isNoContent());

        getProducts(firstAccess, 401);
        getProducts(secondAccess, 401);
        refresh(firstRefresh, 401);
        refresh(secondRefresh, 401);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("password-user", PASSWORD)))
                .andExpect(status().isUnauthorized());
        getProducts(login("password-user", newPassword), 200);
    }

    @Test
    void administrativePasswordResetRevokesAllTargetSessions() throws Exception {
        createUser("reset-admin", PASSWORD, true, false, RoleName.ADMIN);
        var target = createUser("reset-user", PASSWORD, true, false, RoleName.SALES);
        String adminToken = login("reset-admin", PASSWORD);
        String targetLogin = loginResponse("reset-user", PASSWORD);
        String oldAccess = JsonPath.read(targetLogin, "$.accessToken");
        String oldRefresh = JsonPath.read(targetLogin, "$.refreshToken");
        String newPassword = "reset-password-789";

        mockMvc.perform(put("/api/v1/users/{id}/password", target.getId())
                        .header(AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword":"%s"}
                                """.formatted(newPassword)))
                .andExpect(status().isNoContent());

        getProducts(oldAccess, 401);
        refresh(oldRefresh, 401);
        getProducts(login("reset-user", newPassword), 200);
    }

    @Test
    void administratorCanRevokeAllSessionFamiliesWithoutChangingPassword() throws Exception {
        createUser("sessions-admin", PASSWORD, true, false, RoleName.ADMIN);
        var target = createUser("sessions-user", PASSWORD, true, false, RoleName.SALES);
        String adminToken = login("sessions-admin", PASSWORD);
        String firstLogin = loginResponse("sessions-user", PASSWORD);
        String secondLogin = loginResponse("sessions-user", PASSWORD);

        mockMvc.perform(post("/api/v1/users/{id}/sessions/revoke", target.getId())
                        .header(AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        getProducts(JsonPath.read(firstLogin, "$.accessToken"), 401);
        getProducts(JsonPath.read(secondLogin, "$.accessToken"), 401);
        refresh(JsonPath.read(firstLogin, "$.refreshToken"), 401);
        refresh(JsonPath.read(secondLogin, "$.refreshToken"), 401);
        getProducts(login("sessions-user", PASSWORD), 200);
    }

    private void getProducts(String token, int expectedStatus) throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().is(expectedStatus));
    }

    private void refresh(String refreshToken, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().is(expectedStatus));
    }
}
