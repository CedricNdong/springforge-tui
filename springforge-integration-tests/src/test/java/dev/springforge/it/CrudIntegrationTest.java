package dev.springforge.it;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E CRUD integration test using Testcontainers PostgreSQL.
 *
 * <p>Boots the full Spring Boot reference application (with SpringForge-generated
 * layers), connects to a real PostgreSQL database, and exercises every REST
 * endpoint: POST (create), GET (read), PUT (update), DELETE.
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

    static Long createdProductId;
    static Long createdUserId;

    // ── Product CRUD ────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("POST /api/v1/products — should create a product and return 201")
    void shouldCreateProduct() {
        String json = """
            {
                "name": "SpringForge T-Shirt",
                "description": "Official merch",
                "price": 29.99,
                "stock": 100,
                "createdAt": "2026-03-21T10:00:00"
            }
            """;

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/v1/products",
            HttpMethod.POST,
            jsonEntity(json),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("SpringForge T-Shirt");
        assertThat(response.getBody()).contains("29.99");

        createdProductId = extractId(response.getBody());
        assertThat(createdProductId).isNotNull();
    }

    @Test
    @Order(2)
    @DisplayName("GET /api/v1/products/{id} — should retrieve the created product")
    void shouldGetProductById() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/v1/products/" + createdProductId, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("SpringForge T-Shirt");
    }

    @Test
    @Order(3)
    @DisplayName("PUT /api/v1/products/{id} — should update the product")
    void shouldUpdateProduct() {
        String json = """
            {
                "name": "SpringForge Hoodie",
                "description": "Updated merch",
                "price": 59.99,
                "stock": 50,
                "createdAt": "2026-03-21T10:00:00"
            }
            """;

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/v1/products/" + createdProductId,
            HttpMethod.PUT,
            jsonEntity(json),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("SpringForge Hoodie");
        assertThat(response.getBody()).contains("59.99");
    }

    @Test
    @Order(4)
    @DisplayName("DELETE /api/v1/products/{id} — should delete the product")
    void shouldDeleteProduct() {
        ResponseEntity<Void> response = restTemplate.exchange(
            "/api/v1/products/" + createdProductId,
            HttpMethod.DELETE,
            null,
            Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify it's gone
        ResponseEntity<String> getResponse = restTemplate.getForEntity(
            "/api/v1/products/" + createdProductId, String.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ── User CRUD ───────────────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("POST /api/v1/users — should create a user and return 201")
    void shouldCreateUser() {
        String json = """
            {
                "username": "springdev",
                "email": "dev@springforge.dev"
            }
            """;

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/v1/users",
            HttpMethod.POST,
            jsonEntity(json),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("springdev");

        createdUserId = extractId(response.getBody());
    }

    @Test
    @Order(6)
    @DisplayName("GET /api/v1/users/{id} — should retrieve the created user")
    void shouldGetUserById() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/v1/users/" + createdUserId, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("springdev");
        assertThat(response.getBody()).contains("dev@springforge.dev");
    }

    @Test
    @Order(7)
    @DisplayName("GET /api/v1/products — should return paginated list")
    void shouldGetPaginatedProducts() {
        // Create a product first
        String json = """
            {
                "name": "Test Product",
                "description": "For pagination",
                "price": 10.00,
                "stock": 5,
                "createdAt": "2026-03-21T12:00:00"
            }
            """;
        restTemplate.exchange("/api/v1/products", HttpMethod.POST,
            jsonEntity(json), String.class);

        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/v1/products", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("content");
        assertThat(response.getBody()).contains("Test Product");
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private static HttpEntity<String> jsonEntity(String json) {
        var headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return new HttpEntity<>(json, headers);
    }

    private static Long extractId(String jsonBody) {
        // Simple extraction — find "id":N pattern
        var matcher = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*(\\d+)")
            .matcher(jsonBody);
        if (matcher.find()) {
            return Long.parseLong(matcher.group(1));
        }
        return null;
    }
}
