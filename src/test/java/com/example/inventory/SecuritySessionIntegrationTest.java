package com.example.inventory;

import com.example.inventory.users.RoleName;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecuritySessionIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "sessions-password-123";

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void loginWaitingBehindARevocationCannotCreateANewRefreshSession() throws Exception {
        var target = createUser("racing-login", PASSWORD, true, false, RoleName.SALES);
        loginResponse("racing-login", PASSWORD);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Future<Integer>> loginAttempt = new AtomicReference<>();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbcTemplate.queryForObject(
                        "SELECT id FROM app_users WHERE id = ? FOR UPDATE",
                        UUID.class, target.getId());
                Future<Integer> attempt = executor.submit(() -> mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(loginJson("racing-login", PASSWORD)))
                        .andReturn().getResponse().getStatus());
                loginAttempt.set(attempt);
                awaitUserRowLock(attempt);

                assertEquals(1, jdbcTemplate.update("""
                        UPDATE refresh_tokens
                        SET revoked_at = CURRENT_TIMESTAMP
                        WHERE user_id = ? AND revoked_at IS NULL
                        """, target.getId()));
                assertEquals(1, jdbcTemplate.update("""
                        UPDATE app_users
                        SET locked = TRUE,
                            access_token_version = access_token_version + 1,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """, target.getId()));
            });

            assertEquals(401, loginAttempt.get().get(10, TimeUnit.SECONDS));
            assertEquals(0, jdbcTemplate.queryForObject("""
                    SELECT count(*) FROM refresh_tokens
                    WHERE user_id = ? AND revoked_at IS NULL
                    """, Integer.class, target.getId()));
        } finally {
            Future<Integer> attempt = loginAttempt.get();
            if (attempt != null && !attempt.isDone()) {
                try {
                    attempt.get(10, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                    attempt.cancel(true);
                }
            }
            executor.shutdownNow();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

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

    private void awaitUserRowLock(Future<Integer> loginAttempt) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (loginAttempt.isDone()) {
                throw new AssertionError("Login completed before waiting for the user lock");
            }
            Integer waiting = jdbcTemplate.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity
                    WHERE datname = current_database()
                      AND pid <> pg_backend_pid()
                      AND wait_event_type = 'Lock'
                    """, Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for the login lock", exception);
            }
        }
        throw new AssertionError("Login did not wait for the user lock");
    }
}
