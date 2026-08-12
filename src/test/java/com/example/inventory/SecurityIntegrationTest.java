package com.example.inventory;

import com.example.inventory.users.RoleName;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "correct-password-123";

    @Test
    void validLoginReturnsTokensAndInvalidLoginIsGeneric() throws Exception {
        createUser("sales", PASSWORD, true, false, RoleName.SALES);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("SALES@EXAMPLE.COM", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(content().string(not(containsString("passwordHash"))));

        String secret = "wrong-secret-value";
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("missing-user", secret)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication failed"))
                .andExpect(content().string(not(containsString(secret))))
                .andExpect(content().string(not(containsString("missing-user"))));
    }

    @Test
    void missingInvalidAndInsufficientTokensReturn401Or403() throws Exception {
        createUser("sales", PASSWORD, true, false, RoleName.SALES);
        String salesToken = login("sales", PASSWORD);

        mockMvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/products").header(AUTHORIZATION, "Bearer malformed"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(not(containsString("malformed"))));
        mockMvc.perform(patch("/api/inventory/{id}/adjustments", UUID.randomUUID())
                        .header(AUTHORIZATION, "Bearer " + salesToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantityDelta\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreateUsersAndManagerCanAdjustWhileSalesCannot() throws Exception {
        createUser("admin", PASSWORD, true, false, RoleName.ADMIN);
        createUser("manager", PASSWORD, true, false, RoleName.INVENTORY_MANAGER);
        createUser("seller", PASSWORD, true, false, RoleName.SALES);
        String adminToken = login("admin", PASSWORD);
        String managerToken = login("manager", PASSWORD);
        String salesToken = login("seller", PASSWORD);

        mockMvc.perform(post("/api/v1/users")
                        .header(AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"new.user","email":"new.user@example.com",
                                 "password":"another-password-123","roles":["SALES"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("new.user"))
                .andExpect(content().string(not(containsString("another-password-123"))))
                .andExpect(content().string(not(containsString("passwordHash"))));

        String productLocation = mockMvc.perform(post("/api/products")
                        .header(AUTHORIZATION, "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SEC-1","name":"Secured","price":10,"active":true}
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        UUID productId = UUID.fromString(productLocation.substring(productLocation.lastIndexOf('/') + 1));

        mockMvc.perform(patch("/api/inventory/{id}/adjustments", productId)
                        .header(AUTHORIZATION, "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantityDelta\":5}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(5));
        mockMvc.perform(patch("/api/inventory/{id}/adjustments", productId)
                        .header(AUTHORIZATION, "Bearer " + salesToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantityDelta\":1}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/orders")
                        .header(AUTHORIZATION, "Bearer " + salesToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":"%s","quantity":1}]}
                                """.formatted(productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
        mockMvc.perform(get("/api/orders")
                        .header(AUTHORIZATION, "Bearer " + managerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void disabledAndLockedUsersCannotAuthenticate() throws Exception {
        createUser("disabled", PASSWORD, false, false, RoleName.SALES);
        createUser("locked", PASSWORD, true, true, RoleName.SALES);

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("disabled", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication failed"));
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("locked", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication failed"));
    }

    @Test
    void refreshRotatesDetectsReplayAndLogoutRevokesFamily() throws Exception {
        createUser("sales", PASSWORD, true, false, RoleName.SALES);
        String loginBody = loginResponse("sales", PASSWORD);
        String firstRefresh = JsonPath.read(loginBody, "$.refreshToken");

        String rotatedBody = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + firstRefresh + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String secondRefresh = JsonPath.read(rotatedBody, "$.refreshToken");

        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + firstRefresh + "\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + secondRefresh + "\"}"))
                .andExpect(status().isUnauthorized());

        String freshLogin = loginResponse("sales", PASSWORD);
        String logoutRefresh = JsonPath.read(freshLogin, "$.refreshToken");
        mockMvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + logoutRefresh + "\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + logoutRefresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void migrationCreatesRolesRelationsConstraintsAndIndexes() {
        assertEquals(3, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM roles", Integer.class));
        List<String> tables = jdbcTemplate.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema='public' AND table_name IN ('app_users','roles','user_roles','refresh_tokens')
                """, String.class);
        assertEquals(4, tables.size());
        Integer foreignKeys = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pg_constraint
                WHERE contype='f' AND conrelid IN ('user_roles'::regclass,'refresh_tokens'::regclass)
                """, Integer.class);
        assertTrue(foreignKeys >= 3);
        Integer indexes = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname='public' AND tablename IN ('app_users','user_roles','refresh_tokens')
                """, Integer.class);
        assertTrue(indexes >= 7);
    }

    @Test
    void corsIsRestrictiveAndSwaggerIsClosed() throws Exception {
        mockMvc.perform(options("/api/products")
                        .header("Origin", "https://allowed.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_ORIGIN, "https://allowed.example"));
        mockMvc.perform(options("/api/products")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(ACCESS_CONTROL_ALLOW_ORIGIN));

        createUser("admin", PASSWORD, true, false, RoleName.ADMIN);
        String adminToken = login("admin", PASSWORD);
        mockMvc.perform(get("/v3/api-docs").header(AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    private static void assertEquals(int expected, Integer actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }

    private static void assertTrue(boolean condition) {
        org.junit.jupiter.api.Assertions.assertTrue(condition);
    }
}
