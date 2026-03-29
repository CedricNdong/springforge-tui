# SpringForge TUI — Technical Design Document

**Version:** 1.1
**Date:** March 28, 2026
**Status:** In Progress — updated to reflect actual implementation
**Companion document:** [SpringForge TUI PRD v2.1]
**Owner:** Engineering Team

> This document covers **how** SpringForge TUI is built. The PRD covers **what** and **why**.  
> When this doc conflicts with the PRD, the PRD wins on scope/priority; this doc wins on implementation.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Technology Stack](#2-technology-stack)
3. [Module Structure](#3-module-structure)
4. [Data Models](#4-data-models)
5. [TUI Implementation (TamboUI)](#5-tui-implementation-tambui)
6. [Generation Engine](#6-generation-engine)
7. [Template System](#7-template-system)
8. [Configuration System](#8-configuration-system)
9. [Non-Functional Implementation Notes](#9-non-functional-implementation-notes)
10. [Deployment & Distribution](#10-deployment--distribution)
11. [Observability](#11-observability)
12. [Risks & Tradeoffs](#12-risks--tradeoffs)
13. [Open Technical Questions](#13-open-technical-questions)

---

## 1. Architecture Overview

### 1.1 Component Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                          SpringForge TUI                            │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                       CLI Layer (Picocli)                   │   │
│  │   GenerateCommand │ InitCommand │ PreviewCommand            │   │
│  └────────────────────────────┬────────────────────────────────┘   │
│                               │                                     │
│         ┌─────────────────────┴──────────────────────┐             │
│         ▼                                             ▼             │
│  ┌─────────────┐                           ┌──────────────────┐    │
│  │  TUI Layer  │                           │  Non-Interactive │    │
│  │  (TamboUI)  │                           │  Pipeline Mode   │    │
│  │  6 screens  │                           │  (stdout only)   │    │
│  └──────┬──────┘                           └────────┬─────────┘    │
│         │                                           │              │
│         └──────────────┬────────────────────────────┘              │
│                        ▼                                            │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                   TuiRenderer (abstraction interface)        │   │
│  └─────────────────────────────┬───────────────────────────────┘   │
│                                ▼                                    │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                    Generation Engine                         │   │
│  │                                                             │   │
│  │   EntityScanner → EntityParser → TemplateRenderer           │   │
│  │                                      │                      │   │
│  │                               OutputResolver                │   │
│  │                                      │                      │   │
│  │                               ConflictChecker               │   │
│  │                                      │                      │   │
│  │                               FileWriter                    │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌──────────────────────┐   ┌──────────────────────────────────┐   │
│  │   Config System      │   │        Template System           │   │
│  │   springforge.yml    │   │   Built-in + User overrides      │   │
│  │   + CLI flag merging │   │   Mustache templates             │   │
│  └──────────────────────┘   └──────────────────────────────────┘   │
│                                                                     │
│  ┌──────────────────────┐   ┌──────────────────────────────────┐   │
│  │   Java AST Parser    │   │   YAML Entity Parser             │   │
│  │   (JavaParser)       │   │   (SnakeYAML)                    │   │
│  └──────────────────────┘   └──────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

### 1.2 Data Flow — Interactive Mode

```
User runs: springforge generate
      │
      ▼
Picocli parses args → launches TuiApp
      │
      ▼
EntityScanner scans src/ → List<EntityDescriptor>
      │
      ▼
S2: User selects entities → Set<EntityDescriptor>
      │
      ▼
S3: User configures layers → GenerationConfig
      │
      ▼
S4: TemplateRenderer renders in-memory → List<GeneratedFilePreview>
      │
      ▼
S5: User confirms → FileWriter writes to disk
      │
      ▼
S6: GenerationReport displayed
```

### 1.3 Data Flow — Non-Interactive Mode

```
springforge generate --all-entities --all --no-tui --overwrite
      │
      ▼
Picocli parses flags → builds GenerationConfig directly
      │
      ▼
EntityScanner → TemplateRenderer → FileWriter → stdout report
```

---

## 2. Technology Stack

| Component | Library | Version | Justification |
|-----------|---------|---------|---------------|
| Language | Java | 21 LTS | Records, sealed classes, pattern matching; GraalVM 25 compatible |
| CLI Framework | Picocli | 4.7+ | Best-in-class GraalVM native support; annotation-driven |
| TUI Framework | TamboUI | pinned commit (see note) | Only modern Java TUI framework; immediate-mode rendering |
| Java AST Parser | JavaParser | 3.25+ | No classpath needed; pure source analysis |
| YAML Parser | SnakeYAML | 2.x | Standard Java YAML; Jackson YAML as alternative |
| Template Engine | Mustache.java | 0.9.x | Logic-less, predictable, no classpath issues for native-image |
| Config Parsing | Jackson YAML | 2.x | Type-safe `springforge.yml` binding |
| Build Tool | Gradle (Groovy DSL) | 8.x | Shadow plugin for fat jar; native-image plugin |
| Native Build | GraalVM native-image | 25+ | Requires reflect-config.json for JavaParser + TamboUI |
| Testing | JUnit 5 + AssertJ | Latest | Standard; GraalVM test support |
| Distribution | JBang + Maven Central | Latest | Zero-install path via JBang |

> **TamboUI pinning:** At M3 kickoff, pin to a specific commit hash in `build.gradle`:  
> ```kotlin
> implementation("dev.tamboui:tamboui-toolkit:<commit-hash>-SNAPSHOT")
> ```  
> Do not use `LATEST` or open version ranges in production builds.

### 2.1 TUI Abstraction Layer

To protect against TamboUI API changes, all TUI calls go through a `TuiRenderer` interface:

```java
public interface TuiRenderer {
    void showSplash(SplashState state);
    void showEntitySelection(EntitySelectionState state, EntitySelectionCallbacks callbacks);
    void showLayerConfig(LayerConfigState state, LayerConfigCallbacks callbacks);
    void showPreview(PreviewState state, PreviewCallbacks callbacks);
    void showProgress(GenerationProgressState state);
    void showSummary(GenerationReport report);
    void showError(ErrorState state, ErrorCallbacks callbacks);

    // Callback interfaces for two-way communication
    interface EntitySelectionCallbacks {
        void onConfirm(List<EntityDescriptor> selected);
        void onCancel();
    }
    interface LayerConfigCallbacks {
        void onConfirm(LayerConfigState config);
        void onBack();
    }
    interface PreviewCallbacks { void onConfirm(); void onBack(); }
    interface ErrorCallbacks { void onRetry(); void onSkip(); void onQuit(); }
}
```

Implementations:
- `PlainCliRenderer` — stdout-only fallback (dumb terminal / `--no-tui`) — **implemented**
- `TamboUiRenderer` — full interactive TUI via TamboUI — **planned, not yet implemented**

> The TUI screens (S1–S6, S8) are implemented as stateless renderers using TamboUI's `Frame`/`Rect` API.
> They are intended to be orchestrated by a `TuiRunner` that manages the screen state machine and key event dispatch.
> Currently, only `PlainCliRenderer` is wired into the CLI commands.

---

## 3. Module Structure

```
springforge-tui/                         ← root Gradle project
├── springforge-cli/                     ← Picocli commands, entry points
│   └── src/main/java/.../cli/
│       ├── MainCommand.java
│       ├── GenerateCommand.java
│       ├── InitCommand.java
│       ├── PreviewCommand.java
│       ├── SpikeCommand.java            ← Week 1 spike proof-of-concept
│       ├── ExitCodes.java
│       └── ManifestVersionProvider.java
│
├── springforge-tui-screens/             ← TamboUI screen implementations
│   └── src/main/java/.../tui/
│       ├── TuiRenderer.java             ← abstraction interface
│       ├── TamboUiRenderer.java         ← TamboUI implementation
│       ├── PlainCliRenderer.java        ← fallback implementation
│       ├── screens/
│       │   ├── SplashScreen.java
│       │   ├── EntitySelectionScreen.java
│       │   ├── LayerConfigScreen.java
│       │   ├── PreviewScreen.java
│       │   ├── ProgressScreen.java
│       │   ├── SummaryScreen.java
│       │   └── ErrorScreen.java
│       └── state/
│           ├── SplashState.java
│           ├── EntitySelectionState.java
│           ├── LayerConfigState.java
│           ├── PreviewState.java
│           ├── GenerationProgressState.java
│           └── ErrorState.java
│
├── springforge-engine/                  ← core generation logic
│   └── src/main/java/.../engine/
│       ├── scanner/EntityScanner.java
│       ├── parser/
│       │   ├── JavaAstEntityParser.java
│       │   └── YamlEntityParser.java
│       ├── renderer/TemplateRenderer.java
│       ├── writer/FileWriter.java
│       ├── writer/ConflictChecker.java
│       └── model/                       ← EntityDescriptor, etc.
│
├── springforge-config/                  ← springforge.yml binding
│   └── src/main/java/.../config/
│       ├── SpringForgeConfig.java
│       └── ConfigLoader.java
│
├── springforge-templates/               ← Mustache templates
│   └── src/main/resources/templates/
│       ├── dto/
│       ├── mapper/
│       ├── repository/
│       ├── service/
│       ├── controller/
│       ├── migration/
│       └── openapi/
│
├── springforge-native/                  ← GraalVM configuration (PLANNED — not yet created)
│   └── src/main/resources/META-INF/native-image/
│       ├── reflect-config.json
│       ├── resource-config.json
│       └── proxy-config.json
│
└── springforge-integration-tests/       ← E2E integration tests (Testcontainers)
    ├── src/main/java/.../model/         ← Reference entities (User, Product, Order)
    ├── src/main/java/.../ReferenceApplication.java
    ├── src/main/resources/application.yml
    └── src/test/java/.../it/
        ├── GeneratedCodeCompilationTest.java  ← Pipeline: scan → parse → generate → write
        ├── CrudIntegrationTest.java           ← Full CRUD with Testcontainers PostgreSQL
        └── EdgeCaseIntegrationTest.java       ← Lombok, SB2, circular refs, ModelMapper
```

---

## 4. Data Models

### 4.1 EntityDescriptor

```java
public record EntityDescriptor(
    String className,
    String packageName,
    List<FieldDescriptor> fields,
    Set<String> classAnnotations,       // @Data, @Builder, @Entity, etc.
    SpringNamespace namespace,           // JAVAX, JAKARTA
    boolean hasLombok,
    String idFieldName,
    String idFieldType
) {}

public record FieldDescriptor(
    String name,
    String type,                         // e.g. "String", "Long", "List<Order>"
    String genericType,                  // inner type if generic, e.g. "Order"
    boolean isId,
    boolean isNullable,
    boolean isUnique,
    RelationType relation,               // ONE_TO_ONE, ONE_TO_MANY, MANY_TO_ONE, MANY_TO_MANY, NONE
    String relatedEntityName,
    boolean isCircularRef                // detected by scanner
) {}

public enum RelationType { NONE, ONE_TO_ONE, ONE_TO_MANY, MANY_TO_ONE, MANY_TO_MANY }
public enum SpringNamespace { JAVAX, JAKARTA }
```

### 4.2 GenerationConfig

```java
public record GenerationConfig(
    List<EntityDescriptor> entities,
    EnumSet<Layer> layers,
    SpringVersion springVersion,         // V2, V3
    MapperLib mapperLib,                 // MAPSTRUCT, MODEL_MAPPER
    ConflictStrategy conflictStrategy,   // SKIP, OVERWRITE
    Path outputBasePath,
    String basePackage,
    boolean dryRun,
    boolean verbose
) {
    public String dtoPackage()        { return basePackage + ".dto"; }
    public String mapperPackage()     { return basePackage + ".mapper"; }
    public String repositoryPackage() { return basePackage + ".repository"; }
    public String servicePackage()    { return basePackage + ".service"; }
    public String controllerPackage() { return basePackage + ".controller"; }
}

public enum Layer {
    DTO_REQUEST, DTO_RESPONSE, MAPPER, REPOSITORY,
    SERVICE, SERVICE_IMPL, CONTROLLER, FILE_UPLOAD,
    LIQUIBASE, FLYWAY
}
```

> **Note:** Liquibase and Flyway are separate `Layer` entries (not a `MIGRATION` + `MigrationTool` combo).
> `MigrationTool` and `OpenApiFormat` enums from the PRD are handled at the CLI flag level but are not part of `GenerationConfig` — the user selects `LIQUIBASE` or `FLYWAY` directly in the layer selection.

### 4.3 GenerationReport

```java
public record GenerationReport(
    int totalFiles,
    int createdFiles,
    int skippedFiles,
    int errorFiles,
    List<FileGenerationResult> results,
    Duration duration
) {}

public record FileGenerationResult(
    String filePath,
    GenerationStatus status,             // CREATED, SKIPPED, ERROR
    String message
) {}
```

### 4.4 YAML Entity Schema

```yaml
# Example: .springforge/entities.yaml
entities:
  - name: Product
    package: com.example.model
    lombok: true
    fields:
      - name: id
        type: Long
        id: true
        generated: true
      - name: name
        type: String
        nullable: false
      - name: price
        type: BigDecimal
      - name: category
        type: Category
        relation: ManyToOne
```

---

## 5. TUI Implementation (TamboUI)

### 5.1 Framework Choice

TamboUI uses **immediate-mode rendering** — the `render()` method returns the full UI tree on every frame, computed from current state. This means:
- No manual diffing needed
- State mutations trigger a full redraw automatically
- All state must be held in explicit state objects (not UI widget state)

Use the **Toolkit DSL** (`ToolkitApp`) for all screens. It handles the event loop, rendering thread, and terminal setup.

### 5.2 Screen Base Pattern

Screens are **stateless renderers** — `final` utility classes with a static `render(Frame, Rect, State)` method. They do not extend `ToolkitApp` and do not handle key events themselves. Key events are dispatched centrally by the TUI runner based on the current screen type.

```java
// Actual screen pattern — stateless renderer
public final class EntitySelectionScreen {

    private EntitySelectionScreen() {}   // utility class, no instances

    public static void render(Frame frame, Rect area, EntitySelectionState state) {
        List<Rect> mainLayout = Layout.vertical()
            .constraints(
                Constraint.length(3),     // header
                Constraint.fill(),        // content
                Constraint.length(3)      // footer
            )
            .split(area);

        renderHeader(frame, mainLayout.get(0), state);

        List<Rect> contentLayout = Layout.horizontal()
            .constraints(
                Constraint.percentage(40),  // entity list
                Constraint.percentage(60)   // detail panel
            )
            .split(mainLayout.get(1));

        renderEntityList(frame, contentLayout.get(0), state);
        renderEntityDetail(frame, contentLayout.get(1), state);
        renderFooter(frame, mainLayout.get(2), state);
    }
}

// Key events handled externally (not inside the screen):
// ↑/↓ → state.moveFocusUp()/moveFocusDown()
// Space → state.toggleSelected()
// A → state.selectAll()
// N → state.selectNone()
// / → enter filter mode
// Tab → callbacks.onConfirm(state.selectedEntities())
// ? → state.toggleHelp()
```

### 5.3 State Models

All state is **immutable records** with `with*` builder methods:

```java
public record SplashState(
    int totalFiles,
    int scannedFiles,
    String currentFile,
    boolean scanComplete,
    String errorMessage
) {
    // Factory: SplashState.initial()
    // Transitions: withProgress(), withComplete(), withError()
    // Computed: progressPercent()
}

public record EntitySelectionState(
    List<EntityDescriptor> entities,
    Set<String> selectedEntityNames,
    int focusedIndex,
    String filterText,
    boolean showHelp
) {
    // Factory: EntitySelectionState.initial(List<EntityDescriptor>)
    // Navigation: moveFocusUp(), moveFocusDown()
    // Selection: toggleSelected(), selectAll(), selectNone()
    // Filter: withFilter(String), filteredEntities()
    // Help: toggleHelp()
    // Queries: hasSelection(), selectedEntities(), focusedEntity()
}

public record LayerConfigState(
    EnumSet<Layer> selectedLayers,
    SpringVersion springVersion,
    MapperLib mapperLib,
    ConflictStrategy conflictStrategy,
    int focusedIndex,
    int entityCount
) {
    // Factory: LayerConfigState.initial(int entityCount)
    // Layer: toggleLayer(Layer)
    // Options: withSpringVersion(), withMapperLib(), withConflictStrategy()
    // Navigation: moveFocusUp(), moveFocusDown()
    // Computed: estimatedFileCount() = entityCount * selectedLayers.size()
}

public record PreviewState(
    List<GeneratedFile> files,
    int selectedFileIndex,
    int scrollOffset
) {
    // Factory: PreviewState.initial(List<GeneratedFile>)
    // Navigation: selectPrevious(), selectNext(), scrollUp(), scrollDown()
    // Query: selectedFile()
}

public record GenerationProgressState(
    int totalFiles,
    int completedFiles,
    int skippedFiles,
    int errorFiles,
    String currentFile,
    List<FileGenerationResult> log,
    OverallStatus overallStatus
) {
    public enum OverallStatus { IN_PROGRESS, DONE, ERROR }
    // Factory: GenerationProgressState.initial(int totalFiles)
    // Transitions: withFileResult(FileGenerationResult), withCurrentFile(String)
    // Computed: progressPercent()
    // Auto-transitions to DONE or ERROR when all files processed
}

public record ErrorState(
    String errorMessage,
    String filePath,       // nullable
    boolean canRetry
) {
    // Factory: ErrorState.of(message), ErrorState.ofFile(message, filePath)
}
```

### 5.4 Screen Layouts

#### S1 — Splash / Scan
```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  ███████╗██████╗ ██████╗ ██╗███╗   ██╗ ██████╗ ███████╗...     │
│  (cyan ASCII logo)                                               │
│                                                                  │
├──Project Scan────────────────────────────────────────────────────┤
│  ████████████████████████████████████  Scan complete!            │
├──────────────────────────────────────────────────────────────────┤
│  Found 12 entity files                                           │
│                                                                  │
│  Press any key to continue...                                    │
└──────────────────────────────────────────────────────────────────┘
```

#### S2 — Entity Selection
```
┌──SpringForge — Entity Selection──────────────────────────────────┐
├────────────────────┬─────────────────────────────────────────────┤
│  Entities (12)     │  Entity Details — User                      │
│                    │                                             │
│  [x] User          │  Package: com.example.model                 │
│  [x] Product       │  Namespace: jakarta                         │
│  [ ] Order         │  Lombok: yes                                │
│  [ ] OrderItem     │                                             │
│  [ ] Category      │  Fields:                                    │
│  ...               │    > Long id (@Id)                          │
│                    │    - String username                         │
│  [A] All  [N] None │    - String email unique                    │
│  [/] Filter        │    - List orders @ONE_TO_MANY               │
│                    │                                             │
│                    │  Annotations: @Entity, @Data, @Builder      │
├────────────────────┴─────────────────────────────────────────────┤
│  Selected: 2 entities   [Tab] Layer Config  [Ctrl+G] Generate    │
│  [?] Help  [q] Quit                                              │
└──────────────────────────────────────────────────────────────────┘
```

#### S3 — Layer Configuration
```
┌──SpringForge — Layer Configuration───────────────────────────────┐
├──Layers to Generate──────┬──Options──────────────────────────────┤
│                          │                                       │
│  [x] DTO (Request)       │  Spring Boot:  ● 3.x  ○ 2.x          │
│  [x] DTO (Response)      │                                       │
│  [x] Mapper              │  Mapper:       ● MapStruct ○ ModelMap │
│  [x] Repository          │                                       │
│  [x] Service Interface   │  On conflict:  ● Skip ○ Overwrite     │
│  [x] ServiceImpl         │                                       │
│  [x] Controller          │                                       │
│  [x] File Upload         │                                       │
│  [x] Liquibase Migration │                                       │
│  [x] Flyway Migration    │                                       │
├──────────────────────────┴───────────────────────────────────────┤
│  Entities: 2 | Layers: 10 | Files: ~20                           │
│  [←] Back  [Tab] Preview  [Ctrl+G] Generate                     │
└──────────────────────────────────────────────────────────────────┘
```

#### S4 — Code Preview
```
┌──SpringForge — Code Preview──────────────────────────────────────┐
├──Files (20)────────┬──UserRequestDto.java────────────────────────┤
│                    │                                             │
│  User              │   1 package com.example.dto;                │
│     >> UserRequ... │   2                                         │
│        UserResp... │   3 import lombok.Data;                     │
│        UserMapp... │   4                                         │
│  Product           │   5 @Data                                   │
│     ProductRequ... │   6 public class UserRequestDto {           │
│     ProductResp... │   7     private String username;            │
│                    │   8     private String email;               │
│                    │   9 }                                       │
├────────────────────┴─────────────────────────────────────────────┤
│  [↑/↓] Select file  [PgUp/PgDn] Scroll  [←] Back  [Ctrl+G] Gen │
└──────────────────────────────────────────────────────────────────┘
```

#### S5 — Generation Progress
```
┌──SpringForge — Generating...─────────────────────────────────────┐
├──────────────────────────────────────────────────────────────────┤
│  ████████████████░░░░░░░░░░░  60%  (12/20 files)                │
├──────────────────────────────────────────────────────────────────┤
│  Current: UserServiceImpl.java                                   │
├──Generation Log──────────────────────────────────────────────────┤
│  [OK]   dto/UserRequestDto.java                                  │
│  [OK]   dto/UserResponseDto.java                                 │
│  [OK]   mapper/UserMapper.java                                   │
│  [SKIP] controller/OrderController.java — already exists         │
│  [ERR]  service/OrderServiceImpl.java — permission denied        │
└──────────────────────────────────────────────────────────────────┘
```

#### S6 — Summary
```
┌──SpringForge — Generation Complete───────────────────────────────┐
├──Summary─────────────────────────────────────────────────────────┤
│                                                                  │
│  Total files:   20                                               │
│  Created:       18                                               │
│  Skipped:       1                                                │
│  Errors:        1                                                │
│  Duration:      150ms                                            │
├──Output Files────────────────────────────────────────────────────┤
│  [OK]   dto/UserRequestDto.java                                  │
│  [OK]   dto/UserResponseDto.java                                 │
│  [SKIP] controller/OrderController.java — already exists         │
│  ...                                                             │
├──────────────────────────────────────────────────────────────────┤
│  [q] Quit  [g] Generate more                                     │
└──────────────────────────────────────────────────────────────────┘
```

#### S8 — Error
```
┌──SpringForge — Error─────────────────────────────────────────────┐
├──Error Details────────────────────────────────────────────────────┤
│                                                                  │
│  Error: Permission denied                                        │
│                                                                  │
│  File: /output/dto/User.java                                     │
│                                                                  │
│  Please check the error above and choose an action.              │
├──────────────────────────────────────────────────────────────────┤
│  [R] Retry  [S] Skip  [Q] Quit                                  │
└──────────────────────────────────────────────────────────────────┘
```

### 5.5 Dumb Terminal Fallback

On startup, detect terminal capability:

```java
boolean isDumbTerminal = System.getenv("TERM") == null
    || System.getenv("TERM").equals("dumb")
    || System.console() == null;

TuiRenderer renderer = isDumbTerminal || noTuiFlag
    ? new PlainCliRenderer()
    : new TamboUiRenderer();
```

`PlainCliRenderer` outputs plain ANSI text to stdout with no interactive input.

---

## 6. Generation Engine

### 6.1 Entity Scanner

```java
public class EntityScanner {
    // Scans a source directory for .java files containing @Entity
    public List<Path> scanForEntityFiles(Path srcDir) { ... }
}
```

Uses `Files.walkFileTree` — no classpath loading. Reads file content, checks for `@Entity` string presence before full parsing (fast pre-filter).

### 6.2 Java AST Parser

```java
public class JavaAstEntityParser {
    public EntityDescriptor parse(Path javaFile) {
        CompilationUnit cu = StaticJavaParser.parse(javaFile);
        ClassOrInterfaceDeclaration clazz = cu.findFirst(ClassOrInterfaceDeclaration.class)
            .orElseThrow(() -> new ParseException("No class found"));
        // extract fields, annotations, relationships...
    }
}
```

**Circular reference detection:**
```java
// Detects A → B → A during batch parsing
public Set<String> detectCircularRefs(List<EntityDescriptor> all) {
    // Build directed graph of relationships
    // DFS to find cycles
    // Return set of field names that form back-references
}
```

Circular back-reference fields have `FieldDescriptor.isCircularRef = true`. The DTO template uses this flag to emit `Long {fieldName}Id` instead of `{RelatedType}ResponseDto {fieldName}`.

### 6.3 Generation Pipeline

```
For each entity in selectedEntities:
  For each layer in selectedLayers:
    1. TemplateContext ctx = TemplateContextBuilder.build(entity, config)
    2. String templatePath = LayerTemplateMap.get(layer)
    3. String rendered = MustacheRenderer.render(templatePath, ctx)
    4. Path outputPath = OutputPathResolver.resolve(entity, layer, config)
    5. GeneratedFilePreview preview = new GeneratedFilePreview(outputPath, rendered)
    
// If dryRun: return previews without writing
// If not dryRun:
  For each preview:
    6. ConflictChecker.check(outputPath, config.conflictStrategy)
    7. If WRITE: FileWriter.write(outputPath, rendered) → FileGenerationResult
```

### 6.4 Output Path Resolver

```java
public class OutputPathResolver {
    public Path resolve(EntityDescriptor entity, Layer layer, GenerationConfig config) {
        String packagePath = switch (layer) {
            case DTO_REQUEST, DTO_RESPONSE -> config.dtoPackage().replace('.', '/');
            case MAPPER        -> config.mapperPackage().replace('.', '/');
            case REPOSITORY    -> config.repositoryPackage().replace('.', '/');
            case SERVICE       -> config.servicePackage().replace('.', '/');
            case SERVICE_IMPL  -> config.servicePackage().replace('.', '/') + "/impl";
            case CONTROLLER, FILE_UPLOAD -> config.controllerPackage().replace('.', '/');
            case MIGRATION     -> "resources/db/changelog";
            case OPENAPI       -> "resources";
        };
        String fileName = FileNameResolver.resolve(entity.className(), layer, config);
        return config.outputBasePath().resolve(packagePath).resolve(fileName);
    }
}
```

---

## 7. Template System

### 7.1 Engine Choice: Mustache

Mustache.java is chosen over Freemarker because:
- Logic-less: forces clean separation between template data and template rendering
- No classpath scanning at runtime: safe for GraalVM native-image
- Simple resource loading: templates as classpath resources

### 7.2 Template Directory

```
springforge-templates/src/main/resources/templates/
├── dto/
│   ├── RequestDto.java.mustache
│   └── ResponseDto.java.mustache
├── mapper/
│   ├── MapstructMapper.java.mustache
│   └── ModelMapperConfig.java.mustache
├── repository/
│   └── Repository.java.mustache
├── service/
│   ├── Service.java.mustache
│   └── ServiceImpl.java.mustache
├── controller/
│   ├── Controller.java.mustache
│   └── FileController.java.mustache
└── migration/
    ├── Liquibase.xml.mustache
    └── Flyway.sql.mustache
```

> **Note:** 11 templates total. No `openapi/` template directory exists yet — OpenAPI generation is planned but not implemented.
> Template file names use PascalCase (e.g. `Liquibase.xml.mustache`, not `liquibase.xml.mustache`).
```

### 7.3 Template Context Variables

All templates receive a `TemplateContext` map with:

```
entity.name              → "User"
entity.nameLower         → "user"
entity.namePlural        → "users"
entity.package           → "com.example.model"
entity.fields            → List<FieldDescriptor>
entity.idField.name      → "id"
entity.idField.type      → "Long"
entity.hasLombok         → true
config.namespace         → "jakarta" or "javax"
config.basePackage       → "com.example"
config.dtoPackage        → "com.example.dto"
config.mapperPackage     → "com.example.mapper"
config.servicePackage    → "com.example.service"
config.controllerPackage → "com.example.controller"
config.apiPath           → "/api/v1/users"
config.springVersion     → "3"
config.mapperLib         → "mapstruct"
config.useLombok         → true
```

### 7.4 Sample Template — Controller

```mustache
package {{config.controllerPackage}};

import {{config.namespace}}.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
{{#config.useLombok}}
import lombok.RequiredArgsConstructor;
{{/config.useLombok}}

@RestController
@RequestMapping("{{config.apiPath}}")
{{#config.useLombok}}@RequiredArgsConstructor{{/config.useLombok}}
public class {{entity.name}}Controller {

    private final {{entity.name}}Service {{entity.nameLower}}Service;

    @GetMapping
    public ResponseEntity<Page<{{entity.name}}ResponseDto>> getAll(Pageable pageable) {
        return ResponseEntity.ok({{entity.nameLower}}Service.findAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<{{entity.name}}ResponseDto> getById(@PathVariable {{entity.idField.type}} id) {
        return ResponseEntity.ok({{entity.nameLower}}Service.findById(id));
    }

    @PostMapping
    public ResponseEntity<{{entity.name}}ResponseDto> create(
            @Valid @RequestBody {{entity.name}}RequestDto dto) {
        return ResponseEntity.status(201).body({{entity.nameLower}}Service.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<{{entity.name}}ResponseDto> update(
            @PathVariable {{entity.idField.type}} id,
            @Valid @RequestBody {{entity.name}}RequestDto dto) {
        return ResponseEntity.ok({{entity.nameLower}}Service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable {{entity.idField.type}} id) {
        {{entity.nameLower}}Service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

### 7.5 Custom Template Override

Users can place templates in `.springforge/templates/` at project root. SpringForge checks this directory first:

```java
public InputStream resolveTemplate(String templatePath) {
    Path userTemplate = projectRoot.resolve(".springforge/templates").resolve(templatePath);
    if (Files.exists(userTemplate)) {
        return Files.newInputStream(userTemplate);
    }
    return getClass().getResourceAsStream("/templates/" + templatePath);
}
```

Custom templates use the same context variables. Validated at generation time (not init time) for v1.

---

## 8. Configuration System

### 8.1 Config Loading

```java
public class ConfigLoader {
    public SpringForgeConfig load(Path configPath, GenerateCommandOptions cliOptions) {
        // 1. Load built-in defaults
        SpringForgeConfig config = SpringForgeConfig.defaults();
        
        // 2. Apply --config file if provided (highest file-level priority)
        if (cliOptions.configPath() != null) {
            config = merge(config, loadYaml(cliOptions.configPath()));
        } else {
            // 3. Apply local springforge.yml if exists
            Path local = Path.of("springforge.yml");
            if (Files.exists(local)) config = merge(config, loadYaml(local));
        }
        
        // 4. Apply CLI flags (always wins)
        config = applyCLIOverrides(config, cliOptions);
        
        return config;
    }
}
```

### 8.2 Config Java Model

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpringForgeConfig {
    String version = "1.0";
    ProjectConfig project;
    GenerationConfigYaml generation;
    NamingConfig naming;
}

public static class ProjectConfig {
    String basePackage = "com.example";
    String srcDir = "src/main/java";
    String resourceDir = "src/main/resources";
    String springBootVersion = "3";
}

public static class GenerationConfigYaml {
    String mapperLib = "mapstruct";       // "mapstruct" | "modelmapper"
    String migrationTool = "none";        // "liquibase" | "flyway" | "none"
    String openApiFormat = "none";        // "yaml" | "json" | "none"
    String onConflict = "skip";           // "skip" | "overwrite"
    boolean lombok = true;
}

public static class NamingConfig {
    String apiPrefix = "/api";
    String apiVersion = "v1";
}
```

> **Note:** `SpringForgeConfig` is a mutable POJO (not a record) for Jackson YAML binding compatibility. Package paths are derived at runtime in `GenerationConfig.dtoPackage()`, etc.

---

## 9. Non-Functional Implementation Notes

### 9.1 GraalVM Native Image

**Required configuration files** (`springforge-native/src/main/resources/META-INF/native-image/`):

- `reflect-config.json` — JavaParser AST classes, SnakeYAML, Jackson, TamboUI widget classes
- `resource-config.json` — Mustache templates on classpath, `springforge.yml` defaults
- `proxy-config.json` — if any JDK proxies used

**Build command:**
```bash
./gradlew :springforge-cli:nativeCompile
```

**Native binary targets:**
- `springforge-linux-x86_64`
- `springforge-macos-aarch64`
- `springforge-macos-x86_64`

> **Week 1 spike deliverable:** Prove `TamboUI + Picocli + JavaParser` produces a working native binary. If `reflect-config.json` cannot be auto-generated completely via the GraalVM tracing agent, document manual additions required.

### 9.2 Performance Targets

| Operation | Target | Strategy |
|-----------|--------|----------|
| App startup (native) | < 100ms | GraalVM native-image |
| Entity scan (500 classes) | < 2s | Pre-filter by `@Entity` string before full AST parse |
| Generate 10 entities × all layers | < 3s | Parallel per-entity generation via virtual threads (Java 21) |
| TUI frame rate | > 30fps | TamboUI immediate-mode; avoid blocking on render thread |

### 9.3 Virtual Threads for Generation

```java
// Java 21 virtual threads for parallel entity generation
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    List<Future<List<FileGenerationResult>>> futures = entities.stream()
        .map(entity -> executor.submit(() -> generateEntity(entity, config)))
        .toList();
    // collect results, update progress state
}
```

### 9.4 Security Notes

- SpringForge reads source files only; it never executes user code
- No network calls in v1 (all generation is offline)
- Output paths are resolved relative to project root; `../` traversal is rejected
- `springforge.yml` is parsed with `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES = false` to be forward-compatible

---

## 10. Deployment & Distribution

### 10.1 JBang

```bash
# Zero-install run
jbang springforge@springforge-tui

# Alias setup
jbang alias add springforge springforge@springforge-tui
springforge generate
```

`jbang-catalog.json` published at GitHub repo root.

### 10.2 GitHub Packages

```xml
<dependency>
  <groupId>dev.springforge</groupId>
  <artifactId>springforge-tui</artifactId>
  <version>1.0.0</version>
</dependency>
```

Fat JAR published to GitHub Packages via Gradle `maven-publish` plugin.
Requires `GITHUB_TOKEN` with `read:packages` scope to consume.

```gradle
publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/CedricNdong/springforge-tui")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

### 10.3 Native Binaries

Published as GitHub Release assets. Install script:

```bash
curl -L https://github.com/CedricNdong/springforge-tui/releases/latest/download/install.sh | bash
```

Detects OS + architecture, downloads correct binary, places in `/usr/local/bin/springforge`.

**GitHub Actions release workflow** builds 3 binaries on tag push (`v*`):
- `springforge-linux-x86_64`
- `springforge-macos-aarch64`
- `springforge-macos-x86_64`

### 10.4 Versioning

Semantic versioning: `MAJOR.MINOR.PATCH`
- MAJOR: breaking CLI or config changes
- MINOR: new features (new layers, new options)
- PATCH: bug fixes, template corrections

---

## 11. Observability

### 11.1 Verbose Mode

`--verbose` flag enables step-by-step logging to stderr:

```
[00:00:00.012] Scanning src/main/java for @Entity classes...
[00:00:00.089] Found 12 entities in 77ms
[00:00:00.091] Parsing User.java...
[00:00:00.134] User.java parsed — 6 fields, 2 relationships, Lombok detected
[00:00:00.136] Rendering UserController.java...
[00:00:00.141] Writing src/main/java/com/example/controller/UserController.java
[00:00:00.143] ✅ UserController.java written (1.2KB)
```

### 11.2 Generation Report (JSON mode)

`--output-format json` emits a machine-readable report to stdout:

```json
{
  "status": "SUCCESS",
  "duration_ms": 1240,
  "entities_processed": 2,
  "files_created": 40,
  "files_skipped": 2,
  "files_errored": 0,
  "results": [
    { "file": "UserController.java", "status": "CREATED", "path": "..." },
    { "file": "OrderController.java", "status": "SKIPPED", "reason": "exists" }
  ]
}
```

### 11.3 Anonymous Telemetry (Opt-In)

On first run, SpringForge asks:

```
Help improve SpringForge TUI? Send anonymous usage data (no source code, no file contents).
[Y]es / [N]o / [A]lways no
```

Data sent (if opted in): entity count, layer selection, Spring version, OS, generation success/error count. No entity names, no code content, no file paths.

---

## 12. Risks & Tradeoffs

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| TamboUI SNAPSHOT breaks mid-development | High | High | Pin to commit; `TuiRenderer` abstraction; JLine3 fallback identified |
| GraalVM native-image incompatible with TamboUI | Medium | High | Week 1 spike; fallback = JVM fat jar only for v1 |
| JavaParser cannot handle complex generics | Medium | Medium | Skip unsupported field types with warning; document limitations |
| Mustache templates produce non-compiling code | Low | High | Integration test matrix compiles every template configuration |
| SnakeYAML CVE / security issue | Low | Low | Pin version; monitor NVD; easy to swap YAML parser |

### Key Tradeoffs

**AST parsing vs. reflection:**  
Chose AST (JavaParser) over runtime reflection. Tradeoff: no classpath needed (good for CI), but complex generics and inheritance require more template logic. Reflection would be simpler for templates but requires compiled classes and breaks in headless environments.

**Mustache vs. Freemarker:**  
Mustache is logic-less — templates cannot contain business logic, which forces all decisions into `TemplateContext` builders. This makes templates more readable and easier to customize, but means more Java code for conditional rendering (Lombok on/off, namespace switching). Accepted tradeoff for v1.

**One merged OpenAPI file vs. per-entity:**  
One merged file is simpler to maintain and matches how real Spring Boot apps publish their API docs. Per-entity would be useful for microservices generating partial specs — deferred to v2.

**Immediate-mode TUI vs. retained-mode:**  
TamboUI immediate-mode means full re-render on every frame. Simpler to reason about state, no stale UI bugs. Slight performance cost vs. retained-mode (dirty checking). Acceptable at terminal frame rates (30fps).

---

## 13. Open Technical Questions

| # | Question | Decision Needed By | Status |
|---|----------|-------------------|--------|
| T-01 | GraalVM + TamboUI native binary — feasible? | End of Week 1 spike | **Resolved** — spike passed |
| T-02 | Which TamboUI commit hash to pin? | M3 kickoff | **Resolved** — pinned to v0.2.0-SNAPSHOT |
| T-03 | Virtual threads for parallel generation — any TamboUI thread-safety issues? | M1 completion | **Resolved** — BatchGenerator uses virtual threads successfully |
| T-04 | Auto-detect Spring Boot version from `pom.xml`/`build.gradle`? Parsing strategy? | M4 | Open — deferred |
| T-05 | PATCH strategy: null-check fields in RequestDto, or separate PatchDto? | Before F-GEN-16 (P1) | Open |
| T-06 | Custom template validation at `init` vs. generation time? | M4 | Open |
| T-07 | Pluralization for non-English entity names — library or simple `+s` heuristic? | M2 | Open |
| T-08 | reflect-config.json — auto-generate via GraalVM tracing agent or maintain manually? | Week 1 spike | Open — springforge-native not yet created |
