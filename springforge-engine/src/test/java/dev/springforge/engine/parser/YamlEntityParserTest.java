package dev.springforge.engine.parser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import dev.springforge.engine.model.EntityDescriptor;
import dev.springforge.engine.model.FieldDescriptor;
import dev.springforge.engine.model.RelationType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlEntityParserTest {

    private final YamlEntityParser parser = new YamlEntityParser();

    @Test
    @DisplayName("should parse YAML entity definitions into EntityDescriptor")
    void shouldParseYamlEntities() throws IOException {
        List<EntityDescriptor> entities =
            parser.parse(Path.of("src/test/resources/yaml/entities.yaml"));

        assertThat(entities).hasSize(1);

        EntityDescriptor product = entities.get(0);
        assertThat(product.className()).isEqualTo("Product");
        assertThat(product.packageName()).isEqualTo("com.example.model");
        assertThat(product.hasLombok()).isTrue();
        assertThat(product.fields()).hasSize(4);
    }

    @Test
    @DisplayName("should detect @Id field from YAML")
    void shouldDetectIdField() throws IOException {
        List<EntityDescriptor> entities =
            parser.parse(Path.of("src/test/resources/yaml/entities.yaml"));

        EntityDescriptor product = entities.get(0);
        assertThat(product.idFieldName()).isEqualTo("id");
        assertThat(product.idFieldType()).isEqualTo("Long");
    }

    @Test
    @DisplayName("should parse relationship types from YAML")
    void shouldParseRelations() throws IOException {
        List<EntityDescriptor> entities =
            parser.parse(Path.of("src/test/resources/yaml/entities.yaml"));

        FieldDescriptor category = entities.get(0).fields().stream()
            .filter(f -> f.name().equals("category"))
            .findFirst().orElseThrow();

        assertThat(category.relation()).isEqualTo(RelationType.MANY_TO_ONE);
        assertThat(category.relatedEntityName()).isEqualTo("Category");
    }

    @Test
    @DisplayName("should parse nullable=false from YAML")
    void shouldParseNullable() throws IOException {
        List<EntityDescriptor> entities =
            parser.parse(Path.of("src/test/resources/yaml/entities.yaml"));

        FieldDescriptor name = entities.get(0).fields().stream()
            .filter(f -> f.name().equals("name"))
            .findFirst().orElseThrow();

        assertThat(name.isNullable()).isFalse();
    }

    @Test
    @DisplayName("should throw when YAML is missing 'entities' key")
    void shouldThrowOnInvalidYaml() {
        assertThatThrownBy(() ->
            parser.parse(Path.of("src/test/resources/yaml/invalid.yaml")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("missing 'entities' key");
    }

    @Test
    @DisplayName("should throw when YAML file does not exist")
    void shouldThrowWhenFileNotFound() {
        assertThatThrownBy(() ->
            parser.parse(Path.of("nonexistent.yaml")))
            .isInstanceOf(Exception.class);
    }
}
