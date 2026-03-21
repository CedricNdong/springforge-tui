package dev.springforge.engine.renderer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import dev.springforge.engine.model.ConflictStrategy;
import dev.springforge.engine.model.EntityDescriptor;
import dev.springforge.engine.model.FieldDescriptor;
import dev.springforge.engine.model.GeneratedFile;
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

class TemplateRendererTest {

    private TemplateRenderer renderer;
    private EntityDescriptor userEntity;

    @BeforeEach
    void setUp() {
        renderer = new TemplateRenderer();
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
            Set.of("Entity", "Data"),
            SpringNamespace.JAKARTA,
            true, "id", "Long"
        );
    }

    private GenerationConfig config(EnumSet<Layer> layers) {
        return new GenerationConfig(
            List.of(userEntity), layers, SpringVersion.V3,
            MapperLib.MAPSTRUCT, ConflictStrategy.SKIP,
            Path.of("target/generated"), "com.example",
            false, false
        );
    }

    @Nested
    @DisplayName("DTO templates")
    class DtoTemplateTest {

        @Test
        @DisplayName("should render RequestDto with Lombok annotations")
        void shouldRenderRequestDto() {
            GenerationConfig cfg = config(EnumSet.of(Layer.DTO_REQUEST));
            List<GeneratedFile> files = renderer.renderAll(cfg);

            assertThat(files).hasSize(1);
            String content = files.get(0).content();
            assertThat(content).contains("package com.example.dto;");
            assertThat(content).contains("public class UserRequestDto");
            assertThat(content).contains("@Data");
            assertThat(content).contains("private String username;");
            assertThat(content).contains("private String email;");
            assertThat(content).doesNotContain("private Long id;");
        }

        @Test
        @DisplayName("should render ResponseDto with all fields including id")
        void shouldRenderResponseDto() {
            GenerationConfig cfg = config(EnumSet.of(Layer.DTO_RESPONSE));
            List<GeneratedFile> files = renderer.renderAll(cfg);

            String content = files.get(0).content();
            assertThat(content).contains("public class UserResponseDto");
            assertThat(content).contains("private Long id;");
            assertThat(content).contains("private String username;");
        }
    }

    @Nested
    @DisplayName("Service templates")
    class ServiceTemplateTest {

        @Test
        @DisplayName("should render Service interface with CRUD methods")
        void shouldRenderServiceInterface() {
            GenerationConfig cfg = config(EnumSet.of(Layer.SERVICE));
            List<GeneratedFile> files = renderer.renderAll(cfg);

            String content = files.get(0).content();
            assertThat(content).contains("public interface UserService");
            assertThat(content).contains("Page<UserResponseDto> findAll(Pageable pageable)");
            assertThat(content).contains("UserResponseDto findById(Long id)");
            assertThat(content).contains("UserResponseDto create(UserRequestDto dto)");
            assertThat(content).contains("UserResponseDto update(Long id, UserRequestDto dto)");
            assertThat(content).contains("void delete(Long id)");
        }

        @Test
        @DisplayName("should render ServiceImpl with constructor injection")
        void shouldRenderServiceImpl() {
            GenerationConfig cfg = config(EnumSet.of(Layer.SERVICE_IMPL));
            List<GeneratedFile> files = renderer.renderAll(cfg);

            String content = files.get(0).content();
            assertThat(content).contains("public class UserServiceImpl implements UserService");
            assertThat(content).contains("@Service");
            assertThat(content).contains("@RequiredArgsConstructor");
            assertThat(content).contains("private final UserRepository userRepository");
            assertThat(content).contains("private final UserMapper userMapper");
        }
    }

    @Nested
    @DisplayName("Repository template")
    class RepositoryTemplateTest {

        @Test
        @DisplayName("should render Repository extending JpaRepository")
        void shouldRenderRepository() {
            GenerationConfig cfg = config(EnumSet.of(Layer.REPOSITORY));
            List<GeneratedFile> files = renderer.renderAll(cfg);

            String content = files.get(0).content();
            assertThat(content).contains(
                "public interface UserRepository extends JpaRepository<User, Long>");
            assertThat(content).contains("@Repository");
        }
    }

    @Nested
    @DisplayName("Controller template")
    class ControllerTemplateTest {

        @Test
        @DisplayName("should render Controller with CRUD endpoints")
        void shouldRenderController() {
            GenerationConfig cfg = config(EnumSet.of(Layer.CONTROLLER));
            List<GeneratedFile> files = renderer.renderAll(cfg);

            String content = files.get(0).content();
            assertThat(content).contains("@RestController");
            assertThat(content).contains("@RequestMapping(\"/api/v1/users\")");
            assertThat(content).contains("@GetMapping");
            assertThat(content).contains("@PostMapping");
            assertThat(content).contains("@PutMapping(\"/{id}\")");
            assertThat(content).contains("@DeleteMapping(\"/{id}\")");
            assertThat(content).contains("jakarta.validation.Valid");
        }

        @Test
        @DisplayName("should render FileController with upload endpoint")
        void shouldRenderFileController() {
            GenerationConfig cfg = config(EnumSet.of(Layer.FILE_UPLOAD));
            List<GeneratedFile> files = renderer.renderAll(cfg);

            String content = files.get(0).content();
            assertThat(content).contains("UserFileController");
            assertThat(content).contains("@PostMapping(\"/{id}/upload\")");
            assertThat(content).contains("MultipartFile");
        }
    }

    @Nested
    @DisplayName("Mapper template")
    class MapperTemplateTest {

        @Test
        @DisplayName("should render MapStruct mapper interface with all CRUD methods")
        void shouldRenderMapstructMapper() {
            GenerationConfig cfg = config(EnumSet.of(Layer.MAPPER));
            List<GeneratedFile> files = renderer.renderAll(cfg);

            String content = files.get(0).content();
            assertThat(content).contains("@Mapper(componentModel = \"spring\")");
            assertThat(content).contains("public interface UserMapper");
            assertThat(content).contains("UserResponseDto toResponseDto(User entity)");
            assertThat(content).contains("User toEntity(UserRequestDto dto)");
            assertThat(content).contains("void updateEntityFromDto(UserRequestDto dto, @MappingTarget User entity)");
            assertThat(content).contains("import org.mapstruct.Mapper;");
            assertThat(content).contains("import org.mapstruct.MappingTarget;");
        }

        @Test
        @DisplayName("should render ModelMapper config with all CRUD methods")
        void shouldRenderModelMapper() {
            GenerationConfig cfg = new GenerationConfig(
                List.of(userEntity), EnumSet.of(Layer.MAPPER), SpringVersion.V3,
                MapperLib.MODEL_MAPPER, ConflictStrategy.SKIP,
                Path.of("target/generated"), "com.example",
                false, false
            );
            List<GeneratedFile> files = renderer.renderAll(cfg);

            String content = files.get(0).content();
            assertThat(content).contains("@Component");
            assertThat(content).contains("public class UserMapper");
            assertThat(content).contains("ModelMapper modelMapper");
            assertThat(content).contains("UserResponseDto toResponseDto(User entity)");
            assertThat(content).contains("User toEntity(UserRequestDto dto)");
            assertThat(content).contains("void updateEntityFromDto(UserRequestDto dto, User entity)");
        }

        @Test
        @DisplayName("should produce independent templates between MapStruct and ModelMapper")
        void shouldProduceIndependentTemplates() {
            GenerationConfig mapstructCfg = config(EnumSet.of(Layer.MAPPER));
            GenerationConfig modelMapperCfg = new GenerationConfig(
                List.of(userEntity), EnumSet.of(Layer.MAPPER), SpringVersion.V3,
                MapperLib.MODEL_MAPPER, ConflictStrategy.SKIP,
                Path.of("target/generated"), "com.example",
                false, false
            );

            String mapstructContent = renderer.renderAll(mapstructCfg).get(0).content();
            String modelMapperContent = renderer.renderAll(modelMapperCfg).get(0).content();

            assertThat(mapstructContent).contains("interface");
            assertThat(mapstructContent).doesNotContain("ModelMapper");
            assertThat(modelMapperContent).contains("class");
            assertThat(modelMapperContent).doesNotContain("@Mapper(componentModel");
        }

        @Test
        @DisplayName("should switch mapper via config mapperLib field")
        void shouldSwitchMapperViaConfig() {
            GenerationConfig mapstructCfg = new GenerationConfig(
                List.of(userEntity), EnumSet.of(Layer.MAPPER), SpringVersion.V3,
                MapperLib.MAPSTRUCT, ConflictStrategy.SKIP,
                Path.of("target/generated"), "com.example",
                false, false
            );
            GenerationConfig modelMapperCfg = new GenerationConfig(
                List.of(userEntity), EnumSet.of(Layer.MAPPER), SpringVersion.V3,
                MapperLib.MODEL_MAPPER, ConflictStrategy.SKIP,
                Path.of("target/generated"), "com.example",
                false, false
            );

            String mapstructContent = renderer.renderAll(mapstructCfg).get(0).content();
            String modelMapperContent = renderer.renderAll(modelMapperCfg).get(0).content();

            assertThat(mapstructContent).contains("@Mapper(componentModel = \"spring\")");
            assertThat(modelMapperContent).contains("@Component");
            assertThat(modelMapperContent).doesNotContain("new ModelMapper()");
            assertThat(modelMapperContent).contains("ModelMapper modelMapper");
        }
    }

    @Nested
    @DisplayName("Spring Boot 2.x support")
    class SpringBoot2Test {

        private GenerationConfig springBoot2Config(EnumSet<Layer> layers) {
            return new GenerationConfig(
                List.of(userEntity), layers, SpringVersion.V2,
                MapperLib.MAPSTRUCT, ConflictStrategy.SKIP,
                Path.of("target/generated"), "com.example",
                false, false
            );
        }

        @Test
        @DisplayName("should use javax namespace in Controller for Spring Boot 2.x")
        void shouldUseJavaxInController() {
            GenerationConfig cfg = springBoot2Config(EnumSet.of(Layer.CONTROLLER));
            List<GeneratedFile> files = renderer.renderAll(cfg);

            String content = files.get(0).content();
            assertThat(content).contains("javax.validation.Valid");
            assertThat(content).doesNotContain("jakarta");
        }

        @Test
        @DisplayName("should use javax namespace in RequestDto for Spring Boot 2.x")
        void shouldUseJavaxInRequestDto() {
            GenerationConfig cfg = springBoot2Config(EnumSet.of(Layer.DTO_REQUEST));
            List<GeneratedFile> files = renderer.renderAll(cfg);

            String content = files.get(0).content();
            assertThat(content).contains("javax.validation.constraints.NotNull");
            assertThat(content).doesNotContain("jakarta");
        }

        @Test
        @DisplayName("should default to Spring Boot 3.x with jakarta namespace")
        void shouldDefaultToJakartaNamespace() {
            GenerationConfig cfg = config(EnumSet.of(Layer.CONTROLLER));
            List<GeneratedFile> files = renderer.renderAll(cfg);

            String content = files.get(0).content();
            assertThat(content).contains("jakarta.validation.Valid");
            assertThat(content).doesNotContain("javax");
        }

        @Test
        @DisplayName("should pass smoke test for all Spring Boot 2.x layers")
        void shouldPassSmokeTestForAllLayers() {
            EnumSet<Layer> layers = EnumSet.of(
                Layer.DTO_REQUEST, Layer.DTO_RESPONSE, Layer.MAPPER,
                Layer.REPOSITORY, Layer.SERVICE, Layer.SERVICE_IMPL,
                Layer.CONTROLLER
            );
            GenerationConfig cfg = springBoot2Config(layers);
            List<GeneratedFile> files = renderer.renderAll(cfg);

            assertThat(files).hasSize(7);
            for (GeneratedFile file : files) {
                assertThat(file.content()).isNotBlank();
            }
        }
    }

    @Nested
    @DisplayName("No Lombok support")
    class NoLombokTest {

        @Test
        @DisplayName("should generate getters/setters when Lombok is absent")
        void shouldGenerateGettersSetters() {
            EntityDescriptor noLombokEntity = new EntityDescriptor(
                "Product", "com.example.model",
                List.of(
                    new FieldDescriptor("id", "Long", null, true, false, false,
                        RelationType.NONE, null, false),
                    new FieldDescriptor("name", "String", null, false, false, false,
                        RelationType.NONE, null, false)
                ),
                Set.of("Entity"),
                SpringNamespace.JAKARTA,
                false, "id", "Long"
            );

            GenerationConfig cfg = new GenerationConfig(
                List.of(noLombokEntity), EnumSet.of(Layer.DTO_RESPONSE),
                SpringVersion.V3, MapperLib.MAPSTRUCT, ConflictStrategy.SKIP,
                Path.of("target/generated"), "com.example",
                false, false
            );
            List<GeneratedFile> files = renderer.renderAll(cfg);

            String content = files.get(0).content();
            assertThat(content).doesNotContain("@Data");
            assertThat(content).contains("getId()");
            assertThat(content).contains("setId(");
            assertThat(content).contains("getName()");
        }

        @Test
        @DisplayName("should generate constructor in ServiceImpl without Lombok")
        void shouldGenerateConstructorInServiceImpl() {
            EntityDescriptor noLombokEntity = new EntityDescriptor(
                "Product", "com.example.model",
                List.of(
                    new FieldDescriptor("id", "Long", null, true, false, false,
                        RelationType.NONE, null, false)
                ),
                Set.of("Entity"),
                SpringNamespace.JAKARTA,
                false, "id", "Long"
            );

            GenerationConfig cfg = new GenerationConfig(
                List.of(noLombokEntity), EnumSet.of(Layer.SERVICE_IMPL),
                SpringVersion.V3, MapperLib.MAPSTRUCT, ConflictStrategy.SKIP,
                Path.of("target/generated"), "com.example",
                false, false
            );
            List<GeneratedFile> files = renderer.renderAll(cfg);

            String content = files.get(0).content();
            assertThat(content).doesNotContain("@RequiredArgsConstructor");
            assertThat(content).contains("public ProductServiceImpl(");
        }
    }

    @Nested
    @DisplayName("Migration templates")
    class MigrationTemplateTest {

        @Test
        @DisplayName("should render Liquibase changelog with createTable changeset")
        void shouldRenderLiquibaseChangelog() {
            GenerationConfig cfg = config(EnumSet.of(Layer.LIQUIBASE));
            List<GeneratedFile> files = renderer.renderAll(cfg);

            assertThat(files).hasSize(1);
            String content = files.get(0).content();
            assertThat(content).contains("<databaseChangeLog");
            assertThat(content).contains("<changeSet id=\"create-user\" author=\"springforge\">");
            assertThat(content).contains("<createTable tableName=\"user\">");
            assertThat(content).contains("<column name=\"id\" type=\"BIGINT\"");
            assertThat(content).contains("primaryKey=\"true\"");
            assertThat(content).contains("<column name=\"username\" type=\"VARCHAR(255)\"");
            assertThat(content).contains("<column name=\"email\" type=\"VARCHAR(255)\"");
            assertThat(content).contains("unique=\"true\"");
        }

        @Test
        @DisplayName("should render Flyway SQL migration with CREATE TABLE")
        void shouldRenderFlywayMigration() {
            GenerationConfig cfg = config(EnumSet.of(Layer.FLYWAY));
            List<GeneratedFile> files = renderer.renderAll(cfg);

            assertThat(files).hasSize(1);
            String content = files.get(0).content();
            assertThat(content).contains("CREATE TABLE user");
            assertThat(content).contains("id BIGINT PRIMARY KEY");
            assertThat(content).contains("username VARCHAR(255)");
            assertThat(content).contains("email VARCHAR(255)");
            assertThat(content).contains("UNIQUE");
        }

        @Test
        @DisplayName("should generate migration scripts without applying them")
        void shouldNotApplyMigrationScripts() {
            GenerationConfig cfg = config(EnumSet.of(Layer.LIQUIBASE));
            List<GeneratedFile> files = renderer.renderAll(cfg);

            assertThat(files).hasSize(1);
            assertThat(files.get(0).layer()).isEqualTo(Layer.LIQUIBASE);
        }

        @Test
        @DisplayName("should resolve Liquibase output to db/changelog directory")
        void shouldResolveLiquibaseOutputPath() {
            GenerationConfig cfg = config(EnumSet.of(Layer.LIQUIBASE));
            List<GeneratedFile> files = renderer.renderAll(cfg);

            assertThat(files.get(0).outputPath().toString())
                .contains("resources/db/changelog");
        }

        @Test
        @DisplayName("should resolve Flyway output to db/migration directory")
        void shouldResolveFlywayOutputPath() {
            GenerationConfig cfg = config(EnumSet.of(Layer.FLYWAY));
            List<GeneratedFile> files = renderer.renderAll(cfg);

            assertThat(files.get(0).outputPath().toString())
                .contains("resources/db/migration");
        }
    }

    @Test
    @DisplayName("should render all layers for a single entity")
    void shouldRenderAllLayers() {
        GenerationConfig cfg = config(EnumSet.allOf(Layer.class));
        List<GeneratedFile> files = renderer.renderAll(cfg);

        assertThat(files).hasSize(Layer.values().length);
    }
}
