package dev.springforge.it;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import dev.springforge.engine.model.ConflictStrategy;
import dev.springforge.engine.model.EntityDescriptor;
import dev.springforge.engine.model.FieldDescriptor;
import dev.springforge.engine.model.GeneratedFile;
import dev.springforge.engine.model.GenerationConfig;
import dev.springforge.engine.model.GenerationReport;
import dev.springforge.engine.model.Layer;
import dev.springforge.engine.model.MapperLib;
import dev.springforge.engine.model.RelationType;
import dev.springforge.engine.model.SpringVersion;
import dev.springforge.engine.parser.JavaAstEntityParser;
import dev.springforge.engine.renderer.BatchGenerator;
import dev.springforge.engine.renderer.TemplateRenderer;
import dev.springforge.engine.scanner.EntityScanner;
import dev.springforge.engine.writer.CodeFileWriter;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E pipeline test: scan → parse → generate → write.
 *
 * <p>Uses the full e-commerce reference model (10 @Entity + 1 non-entity)
 * covering @ManyToOne, @OneToMany, @ManyToMany, @OneToOne, circular refs,
 * primitives, Lombok, and no-Lombok entities.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GeneratedCodeCompilationTest {

    private static final Path ENTITY_DIR = Path.of("src/main/java/com/example/model");
    private static final int ENTITY_COUNT = 10;
    private static final EnumSet<Layer> ALL_LAYERS = EnumSet.of(
        Layer.DTO_REQUEST, Layer.DTO_RESPONSE,
        Layer.MAPPER, Layer.REPOSITORY,
        Layer.SERVICE, Layer.SERVICE_IMPL,
        Layer.CONTROLLER
    );

    private Path outputDir;

    private final EntityScanner scanner = new EntityScanner();
    private final JavaAstEntityParser parser = new JavaAstEntityParser();
    private final TemplateRenderer templateRenderer = new TemplateRenderer();
    private final BatchGenerator batchGenerator = new BatchGenerator(templateRenderer);
    private final CodeFileWriter writer = new CodeFileWriter();

    private List<EntityDescriptor> entities;
    private List<GeneratedFile> generatedFiles;

    @BeforeAll
    void generateAll(@TempDir Path tempOutput) throws Exception {
        this.outputDir = tempOutput;
        List<Path> entityFiles = scanner.scanForEntityFiles(ENTITY_DIR);
        List<EntityDescriptor> parsed = entityFiles.stream()
            .map(f -> {
                try {
                    return parser.parse(f);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
            .toList();
        entities = parser.resolveCircularRefs(parsed);

        GenerationConfig config = new GenerationConfig(
            entities, ALL_LAYERS, SpringVersion.V3,
            MapperLib.MAPSTRUCT, ConflictStrategy.OVERWRITE,
            outputDir, "com.example", false, false
        );
        generatedFiles = batchGenerator.generateAll(config);
        writer.writeAll(generatedFiles, ConflictStrategy.OVERWRITE, outputDir);
    }

    // ── Scanner ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Entity scanner")
    class ScannerTest {

        @Test
        @DisplayName("should find exactly 10 @Entity classes")
        void shouldFindAllEntities() throws Exception {
            List<Path> entityFiles = scanner.scanForEntityFiles(ENTITY_DIR);
            assertThat(entityFiles).hasSize(ENTITY_COUNT);
        }

        @Test
        @DisplayName("should skip Role.java (no @Entity annotation)")
        void shouldSkipNonEntityClasses() throws Exception {
            List<Path> entityFiles = scanner.scanForEntityFiles(ENTITY_DIR);
            Set<String> names = entityFiles.stream()
                .map(p -> p.getFileName().toString())
                .collect(Collectors.toSet());

            assertThat(names).doesNotContain("Role.java");
            assertThat(names).containsExactlyInAnyOrder(
                "User.java", "Product.java", "Category.java",
                "Order.java", "OrderItem.java", "Cart.java",
                "Payment.java", "Review.java", "ShippingAddress.java",
                "Account.java"
            );
        }
    }

    // ── Parser ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Entity parser")
    class ParserTest {

        @Test
        @DisplayName("should parse all 10 entities with correct class names")
        void shouldParseAllEntities() {
            Set<String> classNames = entities.stream()
                .map(EntityDescriptor::className)
                .collect(Collectors.toSet());

            assertThat(classNames).containsExactlyInAnyOrder(
                "User", "Product", "Category", "Order", "OrderItem",
                "Cart", "Payment", "Review", "ShippingAddress", "Account"
            );
        }

        @Test
        @DisplayName("should detect Lombok on entities with @Data")
        void shouldDetectLombok() {
            EntityDescriptor user = findEntity("User");
            EntityDescriptor account = findEntity("Account");

            assertThat(user.hasLombok()).isTrue();
            assertThat(account.hasLombok()).isFalse();
        }

        @Test
        @DisplayName("should parse @ManyToOne relationships")
        void shouldParseManyToOne() {
            EntityDescriptor order = findEntity("Order");
            FieldDescriptor userField = findField(order, "user");

            assertThat(userField.relation()).isEqualTo(RelationType.MANY_TO_ONE);
            assertThat(userField.relatedEntityName()).isEqualTo("User");
        }

        @Test
        @DisplayName("should parse @OneToMany relationships")
        void shouldParseOneToMany() {
            EntityDescriptor category = findEntity("Category");
            FieldDescriptor productsField = findField(category, "products");

            assertThat(productsField.relation()).isEqualTo(RelationType.ONE_TO_MANY);
            assertThat(productsField.relatedEntityName()).isEqualTo("Product");
        }

        @Test
        @DisplayName("should parse @ManyToMany relationships")
        void shouldParseManyToMany() {
            EntityDescriptor cart = findEntity("Cart");
            FieldDescriptor productsField = findField(cart, "products");

            assertThat(productsField.relation()).isEqualTo(RelationType.MANY_TO_MANY);
            assertThat(productsField.relatedEntityName()).isEqualTo("Product");
        }

        @Test
        @DisplayName("should parse @OneToOne relationships")
        void shouldParseOneToOne() {
            EntityDescriptor shippingAddress = findEntity("ShippingAddress");
            FieldDescriptor orderField = findField(shippingAddress, "order");

            assertThat(orderField.relation()).isEqualTo(RelationType.ONE_TO_ONE);
            assertThat(orderField.relatedEntityName()).isEqualTo("Order");
        }

        @Test
        @DisplayName("should parse multiple @ManyToOne in same entity")
        void shouldParseMultipleManyToOne() {
            EntityDescriptor review = findEntity("Review");
            FieldDescriptor productField = findField(review, "product");
            FieldDescriptor userField = findField(review, "user");

            assertThat(productField.relation()).isEqualTo(RelationType.MANY_TO_ONE);
            assertThat(userField.relation()).isEqualTo(RelationType.MANY_TO_ONE);
        }

        @Test
        @DisplayName("should parse primitive field types (int, boolean)")
        void shouldParsePrimitiveTypes() {
            EntityDescriptor review = findEntity("Review");
            FieldDescriptor ratingField = findField(review, "rating");
            assertThat(ratingField.type()).isEqualTo("int");

            EntityDescriptor account = findEntity("Account");
            FieldDescriptor activeField = findField(account, "active");
            assertThat(activeField.type()).isEqualTo("boolean");
        }

        @Test
        @DisplayName("should detect circular refs between Category and Product")
        void shouldDetectCircularRefs() {
            EntityDescriptor category = findEntity("Category");
            EntityDescriptor product = findEntity("Product");

            boolean categoryHasCircular = category.fields().stream()
                .anyMatch(FieldDescriptor::isCircularRef);
            boolean productHasCircular = product.fields().stream()
                .anyMatch(FieldDescriptor::isCircularRef);

            assertThat(categoryHasCircular || productHasCircular).isTrue();
        }

        @Test
        @DisplayName("should detect circular refs between Order and OrderItem")
        void shouldDetectOrderCircularRefs() {
            EntityDescriptor order = findEntity("Order");
            EntityDescriptor orderItem = findEntity("OrderItem");

            boolean orderHasCircular = order.fields().stream()
                .anyMatch(FieldDescriptor::isCircularRef);
            boolean orderItemHasCircular = orderItem.fields().stream()
                .anyMatch(FieldDescriptor::isCircularRef);

            assertThat(orderHasCircular || orderItemHasCircular).isTrue();
        }
    }

    // ── Code Generation ─────────────────────────────────────────────

    @Nested
    @DisplayName("Code generation")
    class GenerationTest {

        @Test
        @DisplayName("should generate 70 files (10 entities × 7 layers)")
        void shouldGenerateCorrectFileCount() {
            assertThat(generatedFiles).hasSize(ENTITY_COUNT * ALL_LAYERS.size());
        }

        @Test
        @DisplayName("should generate all 7 layers for every entity")
        void shouldGenerateAllLayersPerEntity() {
            for (EntityDescriptor entity : entities) {
                for (Layer layer : ALL_LAYERS) {
                    boolean exists = generatedFiles.stream()
                        .anyMatch(f -> f.entityName().equals(entity.className())
                            && f.layer() == layer);
                    assertThat(exists)
                        .as("Layer %s for entity %s", layer, entity.className())
                        .isTrue();
                }
            }
        }

        @Test
        @DisplayName("should write all files to disk")
        void shouldWriteAllFilesToDisk() {
            assertThat(Files.exists(outputDir.resolve(
                "com/example/dto/UserRequestDto.java"))).isTrue();
            assertThat(Files.exists(outputDir.resolve(
                "com/example/dto/UserResponseDto.java"))).isTrue();
            assertThat(Files.exists(outputDir.resolve(
                "com/example/mapper/OrderMapper.java"))).isTrue();
            assertThat(Files.exists(outputDir.resolve(
                "com/example/repository/ProductRepository.java"))).isTrue();
            assertThat(Files.exists(outputDir.resolve(
                "com/example/service/impl/PaymentServiceImpl.java"))).isTrue();
            assertThat(Files.exists(outputDir.resolve(
                "com/example/controller/ReviewController.java"))).isTrue();
        }
    }

    // ── RequestDto Validation ───────────────────────────────────────

    @Nested
    @DisplayName("RequestDto generation")
    class RequestDtoTest {

        @Test
        @DisplayName("should flatten @ManyToOne to Long in RequestDto")
        void shouldFlattenManyToOneToLong() throws Exception {
            String content = readGenerated("com/example/dto/OrderRequestDto.java");
            assertThat(content).contains("private Long userId;");
            assertThat(content).doesNotContain("private User user;");
        }

        @Test
        @DisplayName("should flatten @OneToOne to Long in RequestDto")
        void shouldFlattenOneToOneToLong() throws Exception {
            String content = readGenerated("com/example/dto/ShippingAddressRequestDto.java");
            assertThat(content).contains("private Long orderId;");
            assertThat(content).doesNotContain("private Order order;");
        }

        @Test
        @DisplayName("should flatten multiple @ManyToOne to separate Long fields")
        void shouldFlattenMultipleManyToOne() throws Exception {
            String content = readGenerated("com/example/dto/ReviewRequestDto.java");
            assertThat(content).contains("private Long productId;");
            assertThat(content).contains("private Long userId;");
            assertThat(content).contains("private int rating;");
        }

        @Test
        @DisplayName("should flatten @ManyToMany to Long in RequestDto")
        void shouldFlattenManyToManyToLong() throws Exception {
            String content = readGenerated("com/example/dto/CartRequestDto.java");
            assertThat(content).contains("private Long userId;");
        }

        @Test
        @DisplayName("should not import List when all relationships are flattened")
        void shouldNotImportListWhenFlattened() throws Exception {
            String content = readGenerated("com/example/dto/OrderRequestDto.java");
            assertThat(content).doesNotContain("import java.util.List;");
        }

        @Test
        @DisplayName("should import BigDecimal for entities with BigDecimal fields")
        void shouldImportBigDecimal() throws Exception {
            String content = readGenerated("com/example/dto/PaymentRequestDto.java");
            assertThat(content).contains("import java.math.BigDecimal;");
            assertThat(content).contains("private BigDecimal amount;");
        }

        @Test
        @DisplayName("should use Lombok annotations for Lombok entities")
        void shouldUseLombok() throws Exception {
            String content = readGenerated("com/example/dto/UserRequestDto.java");
            assertThat(content).contains("@Data");
            assertThat(content).contains("@Builder");
        }

        @Test
        @DisplayName("should generate getters/setters for non-Lombok entities")
        void shouldGenerateGettersSetters() throws Exception {
            String content = readGenerated("com/example/dto/AccountRequestDto.java");
            assertThat(content).doesNotContain("@Data");
            assertThat(content).contains("getAccountNumber()");
            assertThat(content).contains("setAccountNumber(");
            assertThat(content).contains("getBalance()");
            assertThat(content).contains("getActive()");
        }
    }

    // ── ResponseDto Validation ──────────────────────────────────────

    @Nested
    @DisplayName("ResponseDto generation")
    class ResponseDtoTest {

        @Test
        @DisplayName("should flatten @ManyToOne to Long in ResponseDto")
        void shouldFlattenManyToOneToLong() throws Exception {
            String content = readGenerated("com/example/dto/OrderResponseDto.java");
            assertThat(content).contains("private Long userId;");
        }

        @Test
        @DisplayName("should flatten @OneToOne to Long in ResponseDto")
        void shouldFlattenOneToOneToLong() throws Exception {
            String content = readGenerated("com/example/dto/ShippingAddressResponseDto.java");
            assertThat(content).contains("private Long orderId;");
        }

        @Test
        @DisplayName("should include id field in ResponseDto")
        void shouldIncludeIdField() throws Exception {
            String content = readGenerated("com/example/dto/UserResponseDto.java");
            assertThat(content).contains("private Long id;");
        }

        @Test
        @DisplayName("should handle primitive types correctly")
        void shouldHandlePrimitives() throws Exception {
            String content = readGenerated("com/example/dto/ReviewResponseDto.java");
            assertThat(content).contains("private int rating;");
        }
    }

    // ── Mapper Validation ───────────────────────────────────────────

    @Nested
    @DisplayName("MapStruct mapper generation")
    class MapperTest {

        @Test
        @DisplayName("should use @Mapping(source) for @ManyToOne in toResponseDto")
        void shouldUseSourceMappingForManyToOne() throws Exception {
            String content = readGenerated("com/example/mapper/OrderMapper.java");
            assertThat(content).contains(
                "@Mapping(source = \"user.id\", target = \"userId\")");
            assertThat(content).contains("OrderResponseDto toResponseDto(Order entity)");
        }

        @Test
        @DisplayName("should use @Mapping(source) for @OneToOne in toResponseDto")
        void shouldUseSourceMappingForOneToOne() throws Exception {
            String content = readGenerated("com/example/mapper/ShippingAddressMapper.java");
            assertThat(content).contains(
                "@Mapping(source = \"order.id\", target = \"orderId\")");
        }

        @Test
        @DisplayName("should use @Mapping(ignore) for relationships in toEntity")
        void shouldUseIgnoreInToEntity() throws Exception {
            String content = readGenerated("com/example/mapper/OrderMapper.java");
            assertThat(content).contains(
                "@Mapping(target = \"user\", ignore = true)");
            assertThat(content).contains(
                "@Mapping(target = \"orderItems\", ignore = true)");
        }

        @Test
        @DisplayName("should handle multiple @ManyToOne with source mappings")
        void shouldHandleMultipleManyToOneSourceMappings() throws Exception {
            String content = readGenerated("com/example/mapper/ReviewMapper.java");
            assertThat(content).contains(
                "@Mapping(source = \"product.id\", target = \"productId\")");
            assertThat(content).contains(
                "@Mapping(source = \"user.id\", target = \"userId\")");
        }

        @Test
        @DisplayName("should use ignore for @ManyToMany in toResponseDto")
        void shouldIgnoreManyToManyInResponseDto() throws Exception {
            String content = readGenerated("com/example/mapper/CartMapper.java");
            assertThat(content).contains(
                "@Mapping(target = \"products\", ignore = true)");
        }

        @Test
        @DisplayName("should have all three mapper methods")
        void shouldHaveThreeMapperMethods() throws Exception {
            String content = readGenerated("com/example/mapper/UserMapper.java");
            assertThat(content).contains("UserResponseDto toResponseDto(User entity)");
            assertThat(content).contains("User toEntity(UserRequestDto dto)");
            assertThat(content).contains(
                "void updateEntityFromDto(UserRequestDto dto, @MappingTarget User entity)");
        }
    }

    // ── Service & Controller ────────────────────────────────────────

    @Nested
    @DisplayName("Service and Controller generation")
    class ServiceControllerTest {

        @Test
        @DisplayName("should generate service with CRUD methods")
        void shouldGenerateService() throws Exception {
            String content = readGenerated("com/example/service/UserService.java");
            assertThat(content).contains("Page<UserResponseDto> findAll(Pageable pageable)");
            assertThat(content).contains("UserResponseDto findById(Long id)");
            assertThat(content).contains("UserResponseDto create(UserRequestDto dto)");
            assertThat(content).contains("UserResponseDto update(Long id, UserRequestDto dto)");
            assertThat(content).contains("void delete(Long id)");
        }

        @Test
        @DisplayName("should generate service impl with @RequiredArgsConstructor for Lombok")
        void shouldUseLombokInServiceImpl() throws Exception {
            String content = readGenerated("com/example/service/impl/UserServiceImpl.java");
            assertThat(content).contains("@RequiredArgsConstructor");
        }

        @Test
        @DisplayName("should generate constructor injection for non-Lombok service")
        void shouldUseConstructorForNonLombok() throws Exception {
            String content = readGenerated("com/example/service/impl/AccountServiceImpl.java");
            assertThat(content).doesNotContain("@RequiredArgsConstructor");
            assertThat(content).contains("public AccountServiceImpl(");
        }

        @Test
        @DisplayName("should generate controller with correct REST endpoints")
        void shouldGenerateController() throws Exception {
            String content = readGenerated("com/example/controller/ProductController.java");
            assertThat(content).contains("@RestController");
            assertThat(content).contains("@RequestMapping(\"/api/v1/products\")");
            assertThat(content).contains("@GetMapping");
            assertThat(content).contains("@PostMapping");
            assertThat(content).contains("@PutMapping(\"/{id}\")");
            assertThat(content).contains("@DeleteMapping(\"/{id}\")");
        }

        @Test
        @DisplayName("should generate correct API path for multi-word entity names")
        void shouldGenerateCorrectApiPath() throws Exception {
            String content = readGenerated(
                "com/example/controller/ShippingAddressController.java");
            assertThat(content).contains("@RequestMapping(\"/api/v1/shippingAddresses\")");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private EntityDescriptor findEntity(String name) {
        return entities.stream()
            .filter(e -> e.className().equals(name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Entity not found: " + name));
    }

    private FieldDescriptor findField(EntityDescriptor entity, String name) {
        return entity.fields().stream()
            .filter(f -> f.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                "Field '" + name + "' not found in " + entity.className()));
    }

    private String readGenerated(String relativePath) throws Exception {
        return Files.readString(outputDir.resolve(relativePath));
    }
}
