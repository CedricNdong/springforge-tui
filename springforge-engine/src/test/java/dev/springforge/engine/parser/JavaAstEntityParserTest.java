package dev.springforge.engine.parser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import dev.springforge.engine.model.EntityDescriptor;
import dev.springforge.engine.model.FieldDescriptor;
import dev.springforge.engine.model.RelationType;
import dev.springforge.engine.model.SpringNamespace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavaAstEntityParserTest {

    private final JavaAstEntityParser parser = new JavaAstEntityParser();

    private Path testEntity(String name) {
        return Path.of("src/test/resources/entities/" + name);
    }

    @Nested
    @DisplayName("Entity parsing")
    class EntityParsingTest {

        @Test
        @DisplayName("should parse @Entity class with all field metadata")
        void shouldParseEntityClass() throws IOException {
            EntityDescriptor entity = parser.parse(testEntity("User.java"));

            assertThat(entity.className()).isEqualTo("User");
            assertThat(entity.packageName()).isEqualTo("com.example.model");
            assertThat(entity.classAnnotations()).contains("Entity", "Data", "Builder");
            assertThat(entity.fields()).hasSize(4);
        }

        @Test
        @DisplayName("should detect @Id field name and type")
        void shouldDetectIdField() throws IOException {
            EntityDescriptor entity = parser.parse(testEntity("User.java"));

            assertThat(entity.idFieldName()).isEqualTo("id");
            assertThat(entity.idFieldType()).isEqualTo("Long");
        }

        @Test
        @DisplayName("should detect Lombok presence from class annotations")
        void shouldDetectLombok() throws IOException {
            EntityDescriptor entity = parser.parse(testEntity("User.java"));
            assertThat(entity.hasLombok()).isTrue();

            EntityDescriptor order = parser.parse(testEntity("Order.java"));
            assertThat(order.hasLombok()).isFalse();
        }

        @Test
        @DisplayName("should extract field names and types")
        void shouldExtractFieldNamesAndTypes() throws IOException {
            EntityDescriptor entity = parser.parse(testEntity("User.java"));

            assertThat(entity.fields())
                .extracting(FieldDescriptor::name)
                .containsExactly("id", "username", "email", "orders");

            assertThat(entity.fields())
                .extracting(FieldDescriptor::type)
                .containsExactly("Long", "String", "String", "List<Order>");
        }

        @Test
        @DisplayName("should detect @Column(nullable=false) as non-nullable")
        void shouldDetectNonNullable() throws IOException {
            EntityDescriptor entity = parser.parse(testEntity("User.java"));

            FieldDescriptor username = entity.fields().stream()
                .filter(f -> f.name().equals("username"))
                .findFirst().orElseThrow();

            assertThat(username.isNullable()).isFalse();
        }

        @Test
        @DisplayName("should detect @Column(unique=true)")
        void shouldDetectUnique() throws IOException {
            EntityDescriptor entity = parser.parse(testEntity("User.java"));

            FieldDescriptor email = entity.fields().stream()
                .filter(f -> f.name().equals("email"))
                .findFirst().orElseThrow();

            assertThat(email.isUnique()).isTrue();
        }
    }

    @Nested
    @DisplayName("Relationship detection")
    class RelationshipTest {

        @Test
        @DisplayName("should detect @OneToMany relationship")
        void shouldDetectOneToMany() throws IOException {
            EntityDescriptor entity = parser.parse(testEntity("User.java"));

            FieldDescriptor orders = entity.fields().stream()
                .filter(f -> f.name().equals("orders"))
                .findFirst().orElseThrow();

            assertThat(orders.relation()).isEqualTo(RelationType.ONE_TO_MANY);
            assertThat(orders.genericType()).isEqualTo("Order");
            assertThat(orders.relatedEntityName()).isEqualTo("Order");
        }

        @Test
        @DisplayName("should detect @ManyToOne relationship")
        void shouldDetectManyToOne() throws IOException {
            EntityDescriptor entity = parser.parse(testEntity("Order.java"));

            FieldDescriptor user = entity.fields().stream()
                .filter(f -> f.name().equals("user"))
                .findFirst().orElseThrow();

            assertThat(user.relation()).isEqualTo(RelationType.MANY_TO_ONE);
            assertThat(user.relatedEntityName()).isEqualTo("User");
        }
    }

    @Nested
    @DisplayName("Namespace detection")
    class NamespaceTest {

        @Test
        @DisplayName("should detect jakarta namespace")
        void shouldDetectJakarta() throws IOException {
            EntityDescriptor entity = parser.parse(testEntity("User.java"));
            assertThat(entity.namespace()).isEqualTo(SpringNamespace.JAKARTA);
        }

        @Test
        @DisplayName("should detect javax namespace")
        void shouldDetectJavax() throws IOException {
            EntityDescriptor entity = parser.parse(testEntity("Product.java"));
            assertThat(entity.namespace()).isEqualTo(SpringNamespace.JAVAX);
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTest {

        @Test
        @DisplayName("should skip @Embedded fields with warning")
        void shouldSkipEmbeddedFields() throws IOException {
            EntityDescriptor entity = parser.parse(testEntity("EmbeddedEntity.java"));

            assertThat(entity.fields())
                .extracting(FieldDescriptor::name)
                .doesNotContain("address")
                .contains("id", "name");
        }

        @Test
        @DisplayName("should throw when file has no class declaration")
        void shouldThrowWhenNoClass() {
            assertThatThrownBy(() -> parser.parse(testEntity("EmptyFile.java")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No class found");
        }

        @Test
        @DisplayName("should throw when file does not exist")
        void shouldThrowWhenFileNotFound() {
            assertThatThrownBy(() -> parser.parse(Path.of("nonexistent.java")))
                .isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("Circular reference detection")
    class CircularRefTest {

        @Test
        @DisplayName("should detect circular references between User and Order")
        void shouldDetectCircularRefs() throws IOException {
            EntityDescriptor user = parser.parse(testEntity("User.java"));
            EntityDescriptor order = parser.parse(testEntity("Order.java"));

            List<EntityDescriptor> resolved =
                parser.resolveCircularRefs(List.of(user, order));

            EntityDescriptor resolvedUser = resolved.stream()
                .filter(e -> e.className().equals("User"))
                .findFirst().orElseThrow();

            EntityDescriptor resolvedOrder = resolved.stream()
                .filter(e -> e.className().equals("Order"))
                .findFirst().orElseThrow();

            FieldDescriptor ordersField = resolvedUser.fields().stream()
                .filter(f -> f.name().equals("orders"))
                .findFirst().orElseThrow();

            FieldDescriptor userField = resolvedOrder.fields().stream()
                .filter(f -> f.name().equals("user"))
                .findFirst().orElseThrow();

            assertThat(ordersField.isCircularRef()).isTrue();
            assertThat(userField.isCircularRef()).isTrue();
        }

        @Test
        @DisplayName("should not flag non-circular relations")
        void shouldNotFlagNonCircularRefs() throws IOException {
            EntityDescriptor product = parser.parse(testEntity("Product.java"));

            List<EntityDescriptor> resolved =
                parser.resolveCircularRefs(List.of(product));

            FieldDescriptor category = resolved.get(0).fields().stream()
                .filter(f -> f.name().equals("category"))
                .findFirst().orElseThrow();

            assertThat(category.isCircularRef()).isFalse();
        }
    }
}
