package dev.springforge.engine.renderer;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import dev.springforge.engine.model.ConflictStrategy;
import dev.springforge.engine.model.EntityDescriptor;
import dev.springforge.engine.model.FieldDescriptor;
import dev.springforge.engine.model.GenerationConfig;
import dev.springforge.engine.model.Layer;
import dev.springforge.engine.model.MapperLib;
import dev.springforge.engine.model.RelationType;
import dev.springforge.engine.model.SpringNamespace;
import dev.springforge.engine.model.SpringVersion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiSpecGeneratorTest {

    private EntityDescriptor userEntity;
    private EntityDescriptor productEntity;
    private GenerationConfig config;

    @BeforeEach
    void setUp() {
        userEntity = new EntityDescriptor(
            "User", "com.example.model",
            List.of(
                new FieldDescriptor("id", "Long", null, true, false, false,
                    RelationType.NONE, null, false),
                new FieldDescriptor("username", "String", null, false, false, false,
                    RelationType.NONE, null, false),
                new FieldDescriptor("email", "String", null, false, false, true,
                    RelationType.NONE, null, false)
            ),
            Set.of("Entity"),
            SpringNamespace.JAKARTA,
            true, "id", "Long"
        );

        productEntity = new EntityDescriptor(
            "Product", "com.example.model",
            List.of(
                new FieldDescriptor("id", "Long", null, true, false, false,
                    RelationType.NONE, null, false),
                new FieldDescriptor("name", "String", null, false, false, false,
                    RelationType.NONE, null, false),
                new FieldDescriptor("price", "BigDecimal", null, false, false, false,
                    RelationType.NONE, null, false)
            ),
            Set.of("Entity"),
            SpringNamespace.JAKARTA,
            false, "id", "Long"
        );

        config = new GenerationConfig(
            List.of(userEntity, productEntity),
            EnumSet.allOf(Layer.class),
            SpringVersion.V3,
            MapperLib.MAPSTRUCT,
            ConflictStrategy.SKIP,
            Path.of("target/generated"),
            "com.example",
            false, false
        );
    }

    @Nested
    @DisplayName("YAML format")
    class YamlFormatTest {

        @Test
        @DisplayName("should generate OAS3 spec header")
        void shouldGenerateOas3Header() {
            String yaml = OpenApiSpecGenerator.generateYaml(
                List.of(userEntity), config);

            assertThat(yaml).contains("openapi: 3.0.3");
            assertThat(yaml).contains("title: SpringForge Generated API");
            assertThat(yaml).contains("version: 1.0.0");
        }

        @Test
        @DisplayName("should include all CRUD endpoints for entity")
        void shouldIncludeAllCrudEndpoints() {
            String yaml = OpenApiSpecGenerator.generateYaml(
                List.of(userEntity), config);

            assertThat(yaml).contains("/api/v1/users:");
            assertThat(yaml).contains("/api/v1/users/{id}:");
            assertThat(yaml).contains("get:");
            assertThat(yaml).contains("post:");
            assertThat(yaml).contains("put:");
            assertThat(yaml).contains("delete:");
        }

        @Test
        @DisplayName("should merge multiple entities into one spec")
        void shouldMergeMultipleEntities() {
            String yaml = OpenApiSpecGenerator.generateYaml(
                List.of(userEntity, productEntity), config);

            assertThat(yaml).contains("/api/v1/users:");
            assertThat(yaml).contains("/api/v1/products:");
            assertThat(yaml).contains("UserRequestDto:");
            assertThat(yaml).contains("UserResponseDto:");
            assertThat(yaml).contains("ProductRequestDto:");
            assertThat(yaml).contains("ProductResponseDto:");
        }

        @Test
        @DisplayName("should include component schemas with properties")
        void shouldIncludeComponentSchemas() {
            String yaml = OpenApiSpecGenerator.generateYaml(
                List.of(userEntity), config);

            assertThat(yaml).contains("components:");
            assertThat(yaml).contains("schemas:");
            assertThat(yaml).contains("UserRequestDto:");
            assertThat(yaml).contains("UserResponseDto:");
            assertThat(yaml).contains("username:");
            assertThat(yaml).contains("email:");
            assertThat(yaml).contains("type: string");
            assertThat(yaml).contains("type: integer");
        }

        @Test
        @DisplayName("should exclude id field from request schema")
        void shouldExcludeIdFromRequestSchema() {
            String yaml = OpenApiSpecGenerator.generateYaml(
                List.of(userEntity), config);

            String requestSection = yaml.substring(
                yaml.indexOf("UserRequestDto:"),
                yaml.indexOf("UserResponseDto:"));
            assertThat(requestSection).doesNotContain("id:");
        }

        @Test
        @DisplayName("should include id field in response schema")
        void shouldIncludeIdInResponseSchema() {
            String yaml = OpenApiSpecGenerator.generateYaml(
                List.of(userEntity), config);

            String responseSection = yaml.substring(
                yaml.indexOf("UserResponseDto:"));
            assertThat(responseSection).contains("id:");
        }
    }

    @Nested
    @DisplayName("JSON format")
    class JsonFormatTest {

        @Test
        @DisplayName("should generate valid JSON structure")
        void shouldGenerateJsonStructure() {
            String json = OpenApiSpecGenerator.generateJson(
                List.of(userEntity), config);

            assertThat(json).contains("\"openapi\": \"3.0.3\"");
            assertThat(json).contains("\"title\": \"SpringForge Generated API\"");
            assertThat(json).contains("\"paths\":");
            assertThat(json).contains("\"components\":");
        }

        @Test
        @DisplayName("should include CRUD endpoints in JSON")
        void shouldIncludeCrudEndpointsInJson() {
            String json = OpenApiSpecGenerator.generateJson(
                List.of(userEntity), config);

            assertThat(json).contains("\"/api/v1/users\":");
            assertThat(json).contains("\"/api/v1/users/{id}\":");
            assertThat(json).contains("\"get\":");
            assertThat(json).contains("\"post\":");
            assertThat(json).contains("\"put\":");
            assertThat(json).contains("\"delete\":");
        }

        @Test
        @DisplayName("should merge multiple entities in JSON")
        void shouldMergeMultipleEntitiesInJson() {
            String json = OpenApiSpecGenerator.generateJson(
                List.of(userEntity, productEntity), config);

            assertThat(json).contains("\"/api/v1/users\":");
            assertThat(json).contains("\"/api/v1/products\":");
            assertThat(json).contains("\"UserRequestDto\":");
            assertThat(json).contains("\"ProductRequestDto\":");
        }
    }
}
