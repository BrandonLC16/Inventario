package com.example.inventory;

import com.example.inventory.users.RoleName;
import com.example.inventory.users.UserAccount;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccessTokenRevocationIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "access-revocation-password-123";

    @Test
    void logoutImmediatelyRevokesAccessTokenAndRemainsIdempotent() throws Exception {
        createUser("logout-user", PASSWORD, true, false, RoleName.SALES);
        String login = loginResponse("logout-user", PASSWORD);
        String accessToken = JsonPath.read(login, "$.accessToken");
        String refreshToken = JsonPath.read(login, "$.refreshToken");

        getProducts(accessToken, 200);
        logout(refreshToken);
        getProducts(accessToken, 401);

        String freshAccessToken = JsonPath.read(
                loginResponse("logout-user", PASSWORD), "$.accessToken");
        logout(refreshToken);
        getProducts(freshAccessToken, 200);
    }

    @Test
    void disablingUserImmediatelyRevokesPreviouslyIssuedAccessToken() throws Exception {
        createUser("status-admin", PASSWORD, true, false, RoleName.ADMIN);
        UserAccount target = createUser(
                "status-user", PASSWORD, true, false, RoleName.SALES);
        String adminToken = login("status-admin", PASSWORD);
        String targetToken = login("status-user", PASSWORD);

        getProducts(targetToken, 200);
        mockMvc.perform(patch("/api/v1/users/{id}/status", target.getId())
                        .header(AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled":false,"locked":false}
                                """))
                .andExpect(status().isOk());

        getProducts(targetToken, 401);
    }

    @Test
    void replacingRolesRevokesOldTokenAndFreshTokenUsesCurrentRoles() throws Exception {
        createUser("roles-admin", PASSWORD, true, false, RoleName.ADMIN);
        UserAccount target = createUser(
                "roles-user", PASSWORD, true, false, RoleName.INVENTORY_MANAGER);
        String adminToken = login("roles-admin", PASSWORD);
        String managerToken = login("roles-user", PASSWORD);

        createProduct(managerToken, "ROLE-BEFORE-1", 201);
        mockMvc.perform(put("/api/v1/users/{id}/roles", target.getId())
                        .header(AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles":["SALES"]}
                                """))
                .andExpect(status().isOk());

        createProduct(managerToken, "ROLE-REVOKED-1", 401);
        String salesToken = login("roles-user", PASSWORD);
        createProduct(salesToken, "ROLE-AFTER-1", 403);
    }

    private void logout(String refreshToken) throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isNoContent());
    }

    private void getProducts(String token, int expectedStatus) throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().is(expectedStatus));
    }

    private void createProduct(String token, String sku, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .header(AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"%s","name":"%s","price":10,"active":true}
                                """.formatted(sku, sku)))
                .andExpect(status().is(expectedStatus));
    }
}
