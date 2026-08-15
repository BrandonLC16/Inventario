package com.example.inventory;

import com.example.inventory.security.RsaTestSupport;
import com.example.inventory.users.Role;
import com.example.inventory.users.RoleName;
import com.example.inventory.users.RoleRepository;
import com.example.inventory.users.UserAccount;
import com.example.inventory.users.UserAccountRepository;
import com.example.inventory.users.UserTestFixtures;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Arrays;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class AbstractIntegrationTest {

    private static final RsaTestSupport RSA = RsaTestSupport.create();

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17.5-alpine"));

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        registry.add("inventory.security.jwt.public-key-location", RSA::publicKeyLocation);
        registry.add("inventory.security.jwt.private-key-location", RSA::privateKeyLocation);
        registry.add("inventory.security.jwt.issuer", () -> "inventory-integration");
        registry.add("inventory.security.jwt.audience", () -> "inventory-integration-clients");
        registry.add("inventory.security.cors.allowed-origins[0]", () -> "https://allowed.example");
        registry.add("springdoc.swagger-ui.enabled", () -> false);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    UserAccountRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE authentication_rate_limit_buckets, " +
                        "refresh_tokens, user_roles, app_users, " +
                        "inventory_count_lines, inventory_counts, " +
                        "inventory_transfer_items, inventory_transfers, " +
                        "purchase_receipt_items, purchase_receipts, " +
                        "purchase_order_items, purchase_orders, stock_movements, " +
                        "inventory_reservations, " +
                        "order_items, orders, inventory, supplier_products, products, " +
                        "suppliers, customers CASCADE");
    }

    UserAccount createUser(String username, String password, boolean enabled,
                           boolean locked, RoleName... roleNames) {
        UserAccount user = UserTestFixtures.user(username, username + "@example.com",
                passwordEncoder.encode(password), enabled, locked);
        Arrays.stream(roleNames)
                .map(name -> roleRepository.findByName(name).orElseThrow())
                .forEach(role -> UserTestFixtures.addRole(user, role));
        return userRepository.saveAndFlush(user);
    }

    String login(String identifier, String password) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(identifier, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }

    String loginResponse(String identifier, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(identifier, password)))
                .andReturn().getResponse().getContentAsString();
    }

    static String loginJson(String identifier, String password) {
        return """
                {"identifier":"%s","password":"%s"}
                """.formatted(identifier, password);
    }
}
