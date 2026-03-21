package dev.springforge.it;

import com.example.ReferenceApplication;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E CRUD integration test against real PostgreSQL via Testcontainers.
 *
 * <p>Boots the full Spring Boot reference application with SpringForge-generated
 * layers (DTOs, Mappers, Repositories, Services, Controllers) and exercises
 * the REST API end-to-end for multiple entity types.
 *
 * <p>Covers:
 * <ul>
 *   <li>Simple entity CRUD (User — Lombok, Account — no Lombok)</li>
 *   <li>Entity with BigDecimal fields (Account)</li>
 *   <li>Paginated list retrieval</li>
 *   <li>Update existing entity</li>
 *   <li>Delete and verify gone</li>
 *   <li>Entity with relationships endpoint responds (Order with userId)</li>
 * </ul>
 */
@SpringBootTest(
    classes = ReferenceApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CrudIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name",
            () -> "org.postgresql.Driver");
    }

    @Autowired
    TestRestTemplate restTemplate;

    static Long userId;
    static Long accountId;
    static Long categoryId;

    // ── User CRUD (Lombok entity, simple fields) ────────────────────

    @Test
    @Order(1)
    @DisplayName("POST /api/v1/users — should create user and return 201")
    void shouldCreateUser() {
        String json = """
            {
                "name": "Alice",
                "surname": "Smith",
                "email": "alice@example.com"
            }
            """;

        ResponseEntity<String> response = post("/api/v1/users", json);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("Alice");
        assertThat(response.getBody()).contains("Smith");
        assertThat(response.getBody()).contains("alice@example.com");

        userId = extractId(response.getBody());
        assertThat(userId).isNotNull();
    }

    @Test
    @Order(2)
    @DisplayName("GET /api/v1/users/{id} — should retrieve the created user")
    void shouldGetUserById() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/v1/users/" + userId, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Alice");
        assertThat(response.getBody()).contains("alice@example.com");
    }

    @Test
    @Order(3)
    @DisplayName("PUT /api/v1/users/{id} — should update the user")
    void shouldUpdateUser() {
        String json = """
            {
                "name": "Alice",
                "surname": "Johnson",
                "email": "alice.johnson@example.com"
            }
            """;

        ResponseEntity<String> response = put("/api/v1/users/" + userId, json);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Johnson");
        assertThat(response.getBody()).contains("alice.johnson@example.com");
    }

    @Test
    @Order(4)
    @DisplayName("GET /api/v1/users — should return paginated list")
    void shouldGetPaginatedUsers() {
        // Create a second user
        post("/api/v1/users", """
            {
                "name": "Bob",
                "surname": "Brown",
                "email": "bob@example.com"
            }
            """);

        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/v1/users", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("content");
        assertThat(response.getBody()).contains("Alice");
        assertThat(response.getBody()).contains("Bob");
    }

    @Test
    @Order(5)
    @DisplayName("DELETE /api/v1/users/{id} — should delete the user")
    void shouldDeleteUser() {
        // Create a disposable user
        ResponseEntity<String> createResponse = post("/api/v1/users", """
            {
                "name": "ToDelete",
                "surname": "User",
                "email": "delete.me@example.com"
            }
            """);
        Long deleteId = extractId(createResponse.getBody());

        ResponseEntity<Void> response = restTemplate.exchange(
            "/api/v1/users/" + deleteId,
            HttpMethod.DELETE, null, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify it's gone — should return 500 (RuntimeException)
        ResponseEntity<String> getResponse = restTemplate.getForEntity(
            "/api/v1/users/" + deleteId, String.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ── Account CRUD (no Lombok, BigDecimal, primitive boolean) ─────

    @Test
    @Order(10)
    @DisplayName("POST /api/v1/accounts — should create account with BigDecimal")
    void shouldCreateAccount() {
        String json = """
            {
                "accountNumber": "ACC-001",
                "balance": 1500.50,
                "active": true
            }
            """;

        ResponseEntity<String> response = post("/api/v1/accounts", json);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("ACC-001");
        assertThat(response.getBody()).contains("1500.5");
        assertThat(response.getBody()).contains("true");

        accountId = extractId(response.getBody());
    }

    @Test
    @Order(11)
    @DisplayName("GET /api/v1/accounts/{id} — should retrieve account")
    void shouldGetAccountById() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/v1/accounts/" + accountId, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("ACC-001");
    }

    @Test
    @Order(12)
    @DisplayName("PUT /api/v1/accounts/{id} — should update account balance")
    void shouldUpdateAccount() {
        String json = """
            {
                "accountNumber": "ACC-001",
                "balance": 2500.75,
                "active": false
            }
            """;

        ResponseEntity<String> response = put(
            "/api/v1/accounts/" + accountId, json);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("2500.75");
        assertThat(response.getBody()).contains("false");
    }

    // ── Category CRUD (entity with @OneToMany) ──────────────────────

    @Test
    @Order(20)
    @DisplayName("POST /api/v1/categories — should create category")
    void shouldCreateCategory() {
        String json = """
            { "name": "Electronics" }
            """;

        ResponseEntity<String> response = post("/api/v1/categories", json);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("Electronics");

        categoryId = extractId(response.getBody());
    }

    @Test
    @Order(21)
    @DisplayName("GET /api/v1/categories/{id} — should retrieve category")
    void shouldGetCategoryById() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/v1/categories/" + categoryId, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Electronics");
    }

    // ── Relationship endpoints respond ──────────────────────────────

    @Test
    @Order(30)
    @DisplayName("GET /api/v1/orders — endpoint exists and returns paginated response")
    void shouldGetOrdersEndpoint() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/v1/orders", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("content");
    }

    @Test
    @Order(31)
    @DisplayName("GET /api/v1/payments — endpoint exists and returns paginated response")
    void shouldGetPaymentsEndpoint() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/v1/payments", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("content");
    }

    @Test
    @Order(32)
    @DisplayName("GET /api/v1/reviews — endpoint exists and returns paginated response")
    void shouldGetReviewsEndpoint() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/v1/reviews", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(33)
    @DisplayName("GET /api/v1/carts — endpoint exists and returns paginated response")
    void shouldGetCartsEndpoint() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/v1/carts", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(34)
    @DisplayName("GET /api/v1/shippingAddresses — endpoint exists")
    void shouldGetShippingAddressEndpoint() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/v1/shippingAddresses", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(35)
    @DisplayName("GET /api/v1/orderItems — endpoint exists")
    void shouldGetOrderItemsEndpoint() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/v1/orderItems", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private ResponseEntity<String> post(String url, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url, HttpMethod.POST,
            new HttpEntity<>(json, headers), String.class);
    }

    private ResponseEntity<String> put(String url, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url, HttpMethod.PUT,
            new HttpEntity<>(json, headers), String.class);
    }

    private static Long extractId(String jsonBody) {
        var matcher = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*(\\d+)")
            .matcher(jsonBody);
        if (matcher.find()) {
            return Long.parseLong(matcher.group(1));
        }
        return null;
    }
}
