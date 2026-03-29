# SpringForge TUI — Product Requirements Document (PRD)

**Version:** 2.1
**Date:** March 28, 2026
**Status:** In Progress — M3 TUI complete, M4 CLI complete, M6 Integration tests complete
**Author:** Product / Architecture Team
**Stack:** Java 21, TamboUI, Picocli, JavaParser, GraalVM Native

> **Changelog v1 → v2:**
> - Fixed config resolution order (was backwards)
> - Added TamboUI SNAPSHOT mitigation plan
> - Promoted GraalVM + TamboUI spike to Week 1 blocker
> - Resolved Java version contradiction → Java 21 LTS
> - Defined `--all` flag in CLI spec
> - Added regeneration story to Non-Goals
> - Closed OpenAPI open question (decision already made in engine spec)
> - Specified DTO strategy for circular relationships
> - Added test matrix
> - Triaged open questions as blockers vs. non-blockers
> - Added outcome-oriented success metrics
> - Downgraded PATCH to P1
> - Extracted implementation details to separate Tech Design Doc
>
> **Changelog v2 → v2.1 (March 28, 2026):**
> - Updated TUI screen specs to match actual implementation
> - Updated Layer enum documentation (LIQUIBASE/FLYWAY separate layers, no OPENAPI layer yet)
> - Added current implementation status appendix (Section 15)
> - Updated keyboard shortcuts to reflect per-screen actual bindings

---

## Table of Contents

1. [Overview](#1-overview)
2. [Goals & Non-Goals](#2-goals--non-goals)
3. [Assumptions](#3-assumptions)
4. [User Personas](#4-user-personas)
5. [CLI Interface Specification](#5-cli-interface-specification)
6. [TUI Interface Specification](#6-tui-interface-specification)
7. [Generation Engine Specification](#7-generation-engine-specification)
8. [Configuration System](#8-configuration-system)
9. [Functional Requirements](#9-functional-requirements)
10. [Non-Functional Requirements](#10-non-functional-requirements)
11. [Error Handling & Edge Cases](#11-error-handling--edge-cases)
12. [Delivery & Release](#12-delivery--release)
13. [Success Metrics](#13-success-metrics)
14. [Open Questions](#14-open-questions)

---

## 1. Overview

### 1.1 Problem Statement

Spring Boot developers write the same boilerplate — controllers, services, repositories, DTOs, mappers — every time a new JPA entity is introduced. The IntelliJ **Auto API Generator** plugin solves this inside one IDE only. Developers using VS Code, Neovim, remote/headless servers, or CI/CD pipelines have no equivalent tool.

No terminal-native tool currently combines full Spring Boot layer generation with a polished, interactive TUI.

### 1.2 Solution

**SpringForge TUI** is a terminal-first, IDE-agnostic CLI tool that:
- Parses existing Java `@Entity` classes or YAML entity definitions
- Generates a complete, production-ready Spring Boot API stack
- Exposes an interactive TUI (powered by TamboUI) for entity selection, layer configuration, and code preview
- Compiles to a GraalVM native binary for < 100ms startup
- Runs headlessly in CI/CD pipelines via non-interactive flags

### 1.3 Value Proposition

> *"The full power of IntelliJ Auto API Generator — in your terminal, for every developer, on every machine."*

> **Implementation details** (data models, widget names, template variables, ASCII layouts) live in the **Tech Design Doc**, not this PRD.

---

## 2. Goals & Non-Goals

### Goals (v1.0)

| ID | Goal |
|----|------|
| G1 | Parse Java `@Entity` / POJO classes via AST (no runtime reflection required) |
| G2 | Parse entity definitions from a YAML schema as an alternative input |
| G3 | Generate all API layers: DTO, Mapper, Repository, Service, ServiceImpl, Controller |
| G4 | Support file upload endpoints generation |
| G5 | Support multi-entity batch generation |
| G6 | Support Lombok, JPA, MyBatis annotations |
| G7 | Generate Liquibase and Flyway migration scripts |
| G8 | Output OpenAPI / Swagger 3 spec (YAML or JSON) |
| G9 | Provide a fully interactive TUI via TamboUI |
| G10 | Provide a non-interactive CLI mode for pipeline use |
| G11 | Compile to a GraalVM native binary *(contingent on Week 1 spike — see Section 3)* |
| G12 | Distribute via JBang and GitHub Packages / GitHub Releases |

### Non-Goals (v1.0)

- GUI / desktop application
- Support for frameworks other than Spring Boot (Quarkus, Micronaut — roadmap v2)
- Database introspection / reverse engineering from a live DB
- **Code diffing / merge strategy for existing files** — v1 supports skip or overwrite only. Users should commit generated files before re-running with `--overwrite`. A region-based merge strategy (comment markers) is planned for v2.
- Authentication / security layer generation (JWT, OAuth2 — roadmap)
- Frontend code generation (React, Angular — roadmap)

---

## 3. Assumptions & Risk Register

| ID | Assumption | Risk Level | Mitigation |
|----|------------|------------|------------|
| A1 | User's project is a standard Maven or Gradle Spring Boot project | Low | Validate with 3 real projects in M1 |
| A2 | **Java 21 LTS is the minimum runtime** (not 17) | Low | Document clearly; no ambiguity |
| A3 | TamboUI will reach sufficient stability for v1 use | **HIGH** | See mitigation plan below |
| A4 | Input entities use standard `javax.persistence` or `jakarta.persistence` | Low | Auto-detect namespace from imports |
| A5 | MapStruct is the default mapper; ModelMapper is secondary | Low | Template separation keeps both independent |
| A6 | Generated code targets Spring Boot 3.x (Jakarta) by default; 2.x via flag | Low | Validated in test matrix |
| A7 | User runs SpringForge from the root of their Spring Boot project | Low | `--config` flag overrides if needed |
| A8 | Liquibase/Flyway scripts are generated but not applied automatically | Low | Document clearly |
| A9 | OpenAPI output is Swagger 3.0 (OAS3) by default; one merged file | Low | Decision made — see Section 7 |

### ⚠️ TamboUI SNAPSHOT Mitigation Plan (A3)

TamboUI `0.2.0-SNAPSHOT` has explicitly unstable APIs. This is the highest dependency risk in the project.

**Mitigation strategy:**
1. **Pin to a specific commit hash** at M3 kickoff — never use `LATEST` in production builds
2. **Define a TUI abstraction layer** (`TuiRenderer` interface) between SpringForge logic and TamboUI calls — allows swapping the TUI backend without rewriting screens
3. **Fallback library identified:** JLine3 raw mode — functional but less polished; activates only if TamboUI is unshippable
4. **Go/no-go gate at M2 completion:** If TamboUI APIs have broken more than twice during M1–M2, evaluate fallback before committing M3 resources

### ⚠️ GraalVM + TamboUI Compatibility — Week 1 Spike (BLOCKER)

**This must be validated before any other development begins.**

TUI frameworks rely on reflection and dynamic class loading — GraalVM native-image's weak spots. If TamboUI + Picocli + JavaParser cannot produce a working native binary, G11 (native binary goal) must be descoped or deferred to v2.

**Spike deliverable (end of Week 1):**
- Hello-world native binary using TamboUI + Picocli compiles and runs in < 100ms
- JavaParser can parse a sample `.java` file in the same native binary
- If spike fails: native binary goal moves to v1.1; v1 ships as JVM-only fat jar

---

## 4. User Personas

### Persona 1 — Alex, Backend Developer (Solo)
- Uses VS Code + terminal, not IntelliJ
- Builds multiple microservices per month
- Wants to scaffold a full CRUD API in < 5 minutes
- Pain: repeating boilerplate for every entity

### Persona 2 — Sara, Tech Lead
- Manages a team of 5 developers on a monorepo
- Wants to enforce code conventions across all services via `springforge.yml`
- Uses CI/CD (GitHub Actions) for generation validation
- Pain: inconsistent layering patterns across the team

### Persona 3 — Mehdi, DevOps / Platform Engineer
- Builds internal scaffolding scripts
- Runs tools in Docker containers and headless environments
- Needs fully scriptable, non-interactive mode
- Pain: existing tools require an IDE or a browser

---

## 5. CLI Interface Specification

### 5.1 Command Structure

```
springforge [COMMAND] [OPTIONS]

Commands:
  generate    Generate API layers from entity classes
  init        Initialize springforge.yml in current project
  preview     Preview generated code without writing files
  version     Show version info
  help        Show help

Global Options:
  -c, --config <path>     Path to springforge.yml (overrides local file)
  -v, --verbose           Enable verbose output
  --no-color              Disable colored output
  --no-tui                Force non-interactive mode
```

### 5.2 Config Resolution Order

Priority from highest to lowest:

1. **CLI flags** (`--overwrite`, `--spring-version 2`, etc.)
2. **`--config <path>`** — explicitly passed config file
3. **`./springforge.yml`** — local file in current working directory
4. **Built-in defaults**

> ✅ *Fix applied: `--config` now correctly takes priority over local `springforge.yml`.*

### 5.3 `generate` Command

```
springforge generate [OPTIONS]

Source options (mutually exclusive):
  -e, --entity <file>         Single Java entity file
  -E, --entities <files...>   Multiple Java entity files
  -d, --dir <path>            Scan directory for @Entity classes
  -y, --yaml <file>           YAML entity definition file
  --all-entities              Auto-discover all @Entity classes in project src/

Layer selection:
  --all                       Generate all layers (default when no layer flag specified)
  --dto                       Generate DTO classes only
  --mapper                    Generate MapStruct mappers only
  --repository                Generate Spring Data repositories only
  --service                   Generate Service + ServiceImpl only
  --controller                Generate REST controllers only
  --migration                 Generate Liquibase/Flyway migration only
  --openapi                   Generate OpenAPI spec only
  --upload                    Include file upload endpoint

Output options:
  -o, --output <path>         Override output base directory
  --overwrite                 Overwrite existing files (default: skip)
  --dry-run                   Print what would be generated, do not write
  --spring-version <2|3>      Target Spring Boot version (default: 3)
  --mapper-lib <mapstruct|modelmapper>
  --db-migration <liquibase|flyway>
  --openapi-format <yaml|json>
```

**Examples:**
```bash
# Interactive TUI (default)
springforge generate

# Generate all layers for User entity (non-interactive)
springforge generate --entity src/main/java/com/example/model/User.java --all --no-tui

# Generate only controller and service, dry run
springforge generate --entities User.java Product.java --controller --service --dry-run

# Batch generate all entities — CI/CD pipeline mode
springforge generate --all-entities --all --no-tui --overwrite
```

### 5.4 Exit Codes

| Code | Meaning |
|------|---------|
| 0 | Success |
| 1 | General error |
| 2 | Invalid arguments |
| 3 | Entity parsing error |
| 4 | File write error |
| 5 | Config not found or invalid |

---

## 6. TUI Interface Specification

> Full widget details, state models, and TamboUI implementation notes live in the **Tech Design Doc**.  
> This section defines screens, user flows, and acceptance criteria only.

### 6.1 Keyboard Shortcuts

Keyboard shortcuts are **per-screen**, not global. Each screen's footer displays available commands.

**Common patterns across screens:**

| Key | Action | Screens |
|-----|--------|---------|
| `↑` / `↓` | Navigate within a list | S2, S3, S4 |
| `Space` | Toggle checkbox / selection | S2, S3 |
| `Tab` | Advance to next screen | S1, S2, S3, S4 |
| `←` (Left arrow) | Go back to previous screen | S3, S4 |
| `Ctrl+G` | Start generation immediately | S2, S3, S4 |
| `q` | Quit | S2, S6 |
| `?` | Toggle help overlay | S2 |

**Per-screen shortcuts:**

| Screen | Keys |
|--------|------|
| S1 — Splash | Any key to continue after scan |
| S2 — Entity Selection | `↑/↓` navigate, `Space` toggle, `A` select all, `N` select none, `/` filter, `Tab` next, `Ctrl+G` generate, `?` help, `q` quit |
| S3 — Layer Config | `↑/↓` navigate layers, `Space` toggle layer, `←` back, `Tab` preview, `Ctrl+G` generate |
| S4 — Code Preview | `↑/↓` select file, `PgUp/PgDn` scroll, `←` back, `Ctrl+G` generate |
| S5 — Progress | No user input (auto-advances to S6 or error screen) |
| S6 — Summary | `q` quit, `g` generate more |
| Error | `R` retry, `S` skip, `Q` quit (retry/skip only when `canRetry`) |

### 6.2 Screen Flow

```
Launch
  └─► [S1] Splash / Scan ──(any key)──► [S2] Entity Selection
                                              │
                                              ├──(Tab)──► [S3] Layer Configuration
                                              │                   │
                                              │                   ├──(Tab)──► [S4] Code Preview
                                              │                   │                   │
                                              │                   │                   └──(Ctrl+G)──► [S5] Progress
                                              │                   │                                       │
                                              │                   └──(Ctrl+G)──► [S5] Progress ──► [S6] Summary
                                              │
                                              └──(Ctrl+G)──► [S5] Progress ──► [S6] Summary
```

- `Ctrl+G` from S2, S3, or S4 skips directly to S5 (Generation Progress)
- `←` from S3 goes back to S2; `←` from S4 goes back to S3
- `?` toggles help overlay on S2 only
- Fatal errors during generation → Error screen (S8) with retry/skip/quit

### 6.3 Screen Acceptance Criteria

**S1 — Splash / Scan**
- Shows ASCII logo (cyan) and progress bar during project scan
- Displays "Found N entity files" and "Press any key to continue..." when scan completes
- User presses any key to advance to S2
- Shows error message in red if scan fails

**S2 — Entity Selection**
- Two-panel layout: entity list with checkboxes (40%) + entity detail panel (60%)
- Detail panel shows: package, namespace, Lombok status, fields with types and annotations
- `A` / `N` selects or deselects all; `/` opens filter input
- `?` toggles help overlay listing all keyboard shortcuts
- `Tab` advances to S3 (Layer Config); `Ctrl+G` skips to generation
- Footer shows selected entity count

**S3 — Layer Configuration**
- Two-panel layout: layer checkboxes (45%) + options panel (55%)
- 10 layers listed: DTO (Request), DTO (Response), Mapper, Repository, Service, ServiceImpl, Controller, File Upload, Liquibase Migration, Flyway Migration
- Options panel: radio groups for Spring version (2.x/3.x), mapper lib (MapStruct/ModelMapper), conflict strategy (Skip/Overwrite)
- Footer shows: entity count, selected layer count, estimated file count (entities × layers)

**S4 — Code Preview**
- Two-panel layout: file list (30%) + code preview (70%)
- File list grouped by entity name with `>>` marker on selected file
- Code preview with line numbers and basic syntax highlighting (annotations, keywords, comments)
- Scrollable preview panel via `PgUp`/`PgDn`
- Preview renders from templates in-memory; no files written yet

**S5 — Generation Progress**
- Header shows status: "Generating...", "Complete!", or "Completed with errors"
- Overall progress bar with percentage and file count
- Current file display
- Per-file status log: `[OK]` created / `[SKIP]` skipped / `[ERR]` error
- Auto-advances to S6 on completion

**S6 — Summary**
- Stats: total files, created, skipped, errors, duration
- File list with per-file status icons (`[OK]`/`[SKIP]`/`[ERR]`)
- Footer: `[q]` quit, `[g]` generate more

**S8 — Error Screen**
- Error message and file path (if applicable)
- Actions: `[R]` retry, `[S]` skip, `[Q]` quit (retry/skip only when `canRetry` is true)

---

## 7. Generation Engine Specification

### 7.1 Input Formats

| Format | How | Library |
|--------|-----|---------|
| Java `@Entity` class | AST parsing of `.java` source file | JavaParser |
| YAML entity definition | Schema parsing | SnakeYAML |

Both produce an identical internal `EntityDescriptor` model. See Tech Design Doc for schema.

### 7.2 Generated Files Per Entity

| Layer (enum) | File | Output path |
|-------|------|-------------|
| `DTO_REQUEST` | `{Entity}RequestDto.java` | `dto/` |
| `DTO_RESPONSE` | `{Entity}ResponseDto.java` | `dto/` |
| `MAPPER` | `{Entity}Mapper.java` | `mapper/` |
| `REPOSITORY` | `{Entity}Repository.java` | `repository/` |
| `SERVICE` | `{Entity}Service.java` | `service/` |
| `SERVICE_IMPL` | `{Entity}ServiceImpl.java` | `service/impl/` |
| `CONTROLLER` | `{Entity}Controller.java` | `controller/` |
| `FILE_UPLOAD` | `{Entity}FileController.java` | `controller/` |
| `LIQUIBASE` | `V{timestamp}__create_{entity}.xml` | `resources/db/changelog/` |
| `FLYWAY` | `V{timestamp}__create_{entity}.sql` | `resources/db/migration/` |

> **Note:** Liquibase and Flyway are separate layers in the `Layer` enum (not a single `MIGRATION` layer).
> OpenAPI spec generation is defined in the CLI flags but does not yet have a corresponding `Layer` enum entry — planned for a future milestone.

### 7.3 Controller — Generated Endpoints

| Method | Path | Priority |
|--------|------|----------|
| `GET` | `/api/{entities}` (paginated) | P0 |
| `GET` | `/api/{entities}/{id}` | P0 |
| `POST` | `/api/{entities}` | P0 |
| `PUT` | `/api/{entities}/{id}` (full replace) | P0 |
| `DELETE` | `/api/{entities}/{id}` | P0 |
| `PATCH` | `/api/{entities}/{id}` (partial update) | **P1** |
| `POST` | `/api/{entities}/{id}/upload` | P0 (if `--upload`) |

> ✅ *Fix applied: PATCH downgraded to P1. Correct PATCH implementation with JPA requires a defined strategy (null-check, JSON Merge Patch, Optional fields) — this will be decided and specified in Tech Design Doc before P1 implementation.*

### 7.4 Circular Relationship DTO Strategy

When a circular relationship is detected (e.g., `User → Order → User`), the DTO back-reference field is **replaced with its ID field only**:

```java
// Instead of: OrderResponseDto order (which causes infinite recursion)
Long orderId;  // back-reference replaced with ID
```

This is applied automatically. A `// SpringForge: circular ref resolved — back-reference flattened to ID` comment is added to the generated field.

> ✅ *Fix applied: DTO strategy for circular refs now specified explicitly.*

### 7.5 OpenAPI Output

One merged `openapi.yaml` (or `openapi.json`) is generated covering all selected entities. One file per entity is not supported in v1.

> ✅ *Fix applied: open question closed, decision documented here.*

---

## 8. Configuration System

### 8.1 `springforge.yml` Schema

```yaml
version: "1.0"

project:
  basePackage: com.example
  srcDir: src/main/java
  resourceDir: src/main/resources
  springBootVersion: "3"        # "2" or "3"

generation:
  mapperLib: mapstruct           # mapstruct | modelmapper
  migrationTool: none            # liquibase | flyway | none
  openApiFormat: none            # yaml | json | none
  onConflict: skip               # skip | overwrite
  lombok: true

naming:
  apiPrefix: "/api"
  apiVersion: "v1"
```

> **Note:** The config file can optionally be wrapped in a `springforge:` key. Unknown fields are silently ignored for forward compatibility. Package paths (dto, mapper, etc.) are derived from `basePackage` in `GenerationConfig`.

---

## 9. Functional Requirements

### Priority Legend
- **P0** — Must have for launch. Blocking.
- **P1** — Should have for v1. Important but not blocking.
- **P2** — Nice to have. Can ship without.

### 9.1 Entity Parsing

| ID | Priority | Requirement |
|----|----------|-------------|
| F-EP-01 | P0 | Parse standard `@Entity` Java class via AST |
| F-EP-02 | P0 | Extract all field names, types, and JPA annotations |
| F-EP-03 | P0 | Detect `@Id` field and its type |
| F-EP-04 | P0 | Detect relationship annotations: `@OneToMany`, `@ManyToOne`, `@OneToOne`, `@ManyToMany` |
| F-EP-05 | P0 | Detect and respect Lombok annotations |
| F-EP-06 | P0 | Auto-detect Jakarta vs Javax namespace from imports |
| F-EP-07 | P0 | Parse YAML entity definition as input alternative |
| F-EP-08 | P1 | Support `@Embedded` / `@Embeddable` types |
| F-EP-09 | P1 | Support generic types (e.g. `List<Product>`) |
| F-EP-10 | P1 | Support `@MappedSuperclass` inheritance |
| F-EP-11 | P2 | Support Kotlin data classes as entity input |

### 9.2 Code Generation

| ID | Priority | Requirement |
|----|----------|-------------|
| F-GEN-01 | P0 | Generate `RequestDto` and `ResponseDto` for each entity |
| F-GEN-02 | P0 | Generate MapStruct `Mapper` interface |
| F-GEN-03 | P0 | Generate Spring Data JPA `Repository` |
| F-GEN-04 | P0 | Generate `Service` interface with CRUD method signatures |
| F-GEN-05 | P0 | Generate `ServiceImpl` with full CRUD implementation |
| F-GEN-06 | P0 | Generate `RestController` with GET/POST/PUT/DELETE endpoints |
| F-GEN-07 | P0 | Paginate `getAll()` endpoints via `Pageable` |
| F-GEN-08 | P0 | Support selective layer generation |
| F-GEN-09 | P0 | Support multi-entity batch generation |
| F-GEN-10 | P0 | Skip existing files by default; overwrite on `--overwrite` |
| F-GEN-11 | P0 | Generate file upload `POST` endpoint when `--upload` is set |
| F-GEN-12 | P0 | Generate Liquibase XML changelog per entity |
| F-GEN-13 | P0 | Generate Flyway SQL migration per entity |
| F-GEN-14 | P0 | Generate merged OpenAPI 3.0 YAML spec for all selected entities |
| F-GEN-15 | P0 | Apply circular relationship DTO flattening strategy (Section 7.4) |
| F-GEN-16 | P1 | Generate `PATCH` endpoint with null-check partial update strategy |
| F-GEN-17 | P1 | Support ModelMapper as alternative to MapStruct |
| F-GEN-18 | P1 | Generate MyBatis XML mapper and interface |
| F-GEN-19 | P1 | Add `@Operation` and `@Tag` Swagger annotations to controllers |
| F-GEN-20 | P1 | Support Spring Boot 2.x (javax namespace) via `--spring-version 2` |
| F-GEN-21 | P2 | Generate unit test stubs (`@WebMvcTest`, `@DataJpaTest`) |
| F-GEN-22 | P2 | Generate integration test stubs |

### 9.3 TUI

| ID | Priority | Requirement | Status |
|----|----------|-------------|--------|
| F-TUI-01 | P0 | Splash screen with project scan progress | **Done** |
| F-TUI-02 | P0 | Entity selection screen with checkboxes and detail panel | **Done** |
| F-TUI-03 | P0 | Layer configuration screen with all generation options | **Done** |
| F-TUI-04 | P0 | Code preview screen with file list and code preview | **Done** |
| F-TUI-05 | P0 | Generation progress screen with per-file status | **Done** |
| F-TUI-06 | P0 | Summary screen with file list and stats | **Done** |
| F-TUI-07 | P0 | Help overlay on entity selection via `?` | **Done** (S2 only) |
| F-TUI-08 | P0 | Error screen with retry / skip options | **Done** |
| F-TUI-09 | P0 | Keyboard-only navigation | **Done** |
| F-TUI-10 | P0 | Auto-fallback to `--no-tui` mode when dumb terminal detected | **Done** |
| F-TUI-11 | P1 | Filter on entity list via `/` | **Done** |
| F-TUI-12 | P1 | Theme switching (dark / light) via TCSS | Not started |
| F-TUI-13 | P2 | Mouse support for click navigation | Not started |

### 9.4 CLI

| ID | Priority | Requirement |
|----|----------|-------------|
| F-CLI-01 | P0 | `springforge generate` with all documented flags including `--all` |
| F-CLI-02 | P0 | `springforge init` wizard |
| F-CLI-03 | P0 | `--no-tui` flag for fully non-interactive mode |
| F-CLI-04 | P0 | `--dry-run` flag |
| F-CLI-05 | P0 | Documented and implemented exit codes |
| F-CLI-06 | P1 | `springforge preview` — render preview to stdout |
| F-CLI-07 | P1 | `--output-format json` — machine-readable generation report |
| F-CLI-08 | P2 | Shell completion scripts (bash, zsh, fish) |

---

## 10. Non-Functional Requirements

| ID | Category | Requirement |
|----|----------|-------------|
| NF-01 | Performance | GraalVM native binary starts in < 100ms *(contingent on Week 1 spike)* |
| NF-02 | Performance | Generate 10 entities × all layers in < 3 seconds |
| NF-03 | Performance | Project entity scan completes in < 2 seconds for projects with < 500 classes |
| NF-04 | Usability | First-time user reaches first generated file in < 5 minutes |
| NF-05 | Compatibility | Runs on macOS, Linux, Windows (WSL) |
| NF-06 | Compatibility | JVM mode requires Java 21+ |
| NF-07 | Compatibility | Native binary targets macOS ARM64, macOS x86_64, Linux x86_64 |
| NF-08 | Quality | Generated code compiles with no errors in > 90% of standard Spring Boot 3 projects |
| NF-09 | Quality | Test matrix (see Section 12.3) covered by integration tests |
| NF-10 | Maintainability | Adding a new template requires no changes to core engine code |
| NF-11 | Distribution | Available via `jbang` (zero install) and GitHub Packages |
| NF-12 | Observability | `--verbose` logs each generation step with timing |

---

## 11. Error Handling & Edge Cases

| Scenario | Behavior |
|----------|----------|
| Entity file not found | Exit code 3, clear error with file path |
| File has no `@Entity` annotation | Warning, entity skipped, generation continues |
| Output directory doesn't exist | Auto-create directory tree |
| File exists + `skip` strategy | Log `"Skipped: {file} (already exists)"`, continue |
| File exists + `overwrite` strategy | Overwrite, log `"Overwritten: {file}"` |
| Entity has no `@Id` field | Warning, generate with `// TODO: add @Id field` placeholder |
| Circular relationship detected | Apply ID-flattening strategy (Section 7.4), add explanatory comment |
| `@Embedded` field (v1) | Log warning `"@Embedded not supported in v1 — field skipped"` |
| Java syntax error in entity file | Exit code 3 with line number and parse error detail |
| `springforge.yml` not found | Use all defaults, print info message |
| `springforge.yml` invalid field | Exit code 5 with field name and expected type |
| No `@Entity` classes found | Empty state in TUI with guidance; exit code 3 in CLI mode |
| Permission denied on output | Exit code 4, suggest `--output` alternative |
| TamboUI dumb terminal detected | Auto-fallback to plain CLI output mode |

---

## 12. Delivery & Release

### 12.1 Milestones

| Milestone | Duration | Exit Criteria |
|-----------|----------|---------------|
| **Week 1 Spike** | 1 week | GraalVM + TamboUI + Picocli native binary runs; go/no-go on G11 |
| **M1 — Core Engine** | 3–4 weeks | Parse 5 entity types, generate all layers, write files, unit tests pass |
| **M2 — Full Layer Stack** | 2–3 weeks | All 10 generation types work, templates validated, OpenAPI output correct |
| **M3 — TUI (TamboUI)** | 2–3 weeks | All 6 screens functional, navigation complete, preview renders correctly |
| **M4 — CLI + Config** | 1 week | All flags work including `--all`, `springforge.yml` parsed, dry-run verified |
| **M5 — Native + Release** | 1–2 weeks | Native binary < 100ms, published to GitHub Packages + JBang, GitHub Release with native binaries |
| **M6 — Integration Tests** | 1 week | E2E pipeline tests (scan → parse → generate → write → compile), CRUD tests with Testcontainers PostgreSQL, edge-case coverage (Lombok, SB2, circular refs, ModelMapper) |

### 12.2 Definition of Done (per feature)

- Code written and peer-reviewed
- Unit tests: happy path + top 3 error cases
- Integration test: generated code compiles against a real Spring Boot 3.x project
- Documented in README or inline help

### 12.3 Test Matrix

Not all configuration combinations can be fully integration-tested in v1. The following defines coverage tiers:

| Tier | Combinations | Coverage |
|------|-------------|----------|
| **Full integration test** | Spring Boot 3.x + MapStruct + JPA + Liquibase + Lombok | All layers, compile verified |
| **Full integration test** | Spring Boot 3.x + MapStruct + JPA + Flyway + Lombok | All layers, compile verified |
| **Smoke test** | Spring Boot 2.x + MapStruct + JPA + Liquibase + Lombok | Compile check, no deep assertion |
| **Smoke test** | Spring Boot 3.x + ModelMapper + JPA + none + no Lombok | Compile check |
| **Smoke test** | Spring Boot 3.x + MapStruct + MyBatis + Liquibase + Lombok | Compile check |
| **Not tested in v1** | Spring Boot 2.x + ModelMapper / Spring Boot 2.x + MyBatis | Documented gap |

> ✅ *Fix applied: test matrix explicitly defines what is and is not covered, replacing the vague NF-09 claim.*

---

## 13. Success Metrics

| Metric | Target | Timeline | Measurement Method |
|--------|--------|----------|--------------------|
| GitHub Stars | > 500 | 3 months | GitHub API |
| JBang installs | > 1,000 | 3 months | GitHub Releases download count |
| Weekly Active Users | > 200 | 3 months | Anonymous usage ping (opt-in) |
| **Task completion rate** | > 80% | 3 months | % of sessions that reach S6 Summary without error |
| **Generated code compiles** | > 90% | Ongoing | CI test matrix (Section 12.3) across all integration-tested combinations |
| **Error rate by entity type** | < 5% parse errors on standard entities | Ongoing | Error telemetry (opt-in), broken down by annotation type |
| Native binary startup | < 100ms | M5 | CI benchmark on each release |
| Time to first generated file | < 5 min | First month | Onboarding survey (GitHub Discussions) |

> ✅ *Fix applied: outcome-oriented metrics added alongside adoption signals, with explicit measurement methods.*

---

## 14. Open Questions

### 🔴 Must Resolve Before Development Starts

| # | Question | Why It Blocks | Status |
|---|----------|---------------|--------|
| OQ-1 | Does TamboUI + Picocli + JavaParser compile to a working GraalVM native binary? | Determines if G11 is achievable. | **Resolved** — Week 1 spike passed (SpikeCommand) |
| OQ-2 | Which commit hash of TamboUI do we pin for M3? | Must be decided at M3 kickoff to avoid API drift mid-sprint | **Resolved** — pinned to v0.2.0-SNAPSHOT |
| OQ-3 | Does SpringForge auto-detect Spring Boot version from `pom.xml` / `build.gradle`? | Affects CLI spec and config schema. | Open — deferred to M5 |
| OQ-4 | What is the PATCH generation strategy? (null-check, JSON Merge Patch, Optional fields) | Required before implementing F-GEN-16. | Open — P1 feature, not yet started |

### 🟡 Can Decide During or After v1

| # | Question | Notes |
|---|----------|-------|
| OQ-5 | Windows native binary (not just WSL) — v1 or v2? | WSL covers most developer use cases; native Windows adds CI complexity |
| OQ-6 | How to handle pluralization for non-English entity names? | e.g., `Commande` → `commandes`? Default: append `s`, override via config |
| OQ-7 | MyBatis + JPA co-existence — is there a valid use case for both simultaneously? | If yes, generate separate mapper files; if no, make them mutually exclusive in config |
| OQ-8 | Custom template DX — validate at `springforge init` time or at generation time? | Init-time gives faster feedback; generation-time is simpler to implement |
| OQ-9 | File conflict resolution per-file in TUI (vs. global setting)? | Power user UX improvement; adds complexity to S3 screen |

---

## 15. Current Implementation Status

> **Last updated:** March 28, 2026

### 15.1 Milestone Status

| Milestone | Status | Notes |
|-----------|--------|-------|
| **Week 1 Spike** | **Done** | `SpikeCommand` validates TamboUI + Picocli + JavaParser in GraalVM native binary |
| **M1 — Core Engine** | **Done** | `EntityScanner`, `JavaAstEntityParser`, `TemplateRenderer`, `BatchGenerator` (virtual threads), `CodeFileWriter` all implemented |
| **M2 — Full Layer Stack** | **Mostly done** | 10 layers in `Layer` enum, 11 Mustache templates. OpenAPI layer defined in CLI flags but no `OPENAPI` enum entry yet |
| **M3 — TUI (TamboUI)** | **Done** | 7 screens (S1–S6 + Error), 5 state records, `TuiRenderer` abstraction, `PlainCliRenderer` fallback, all screens use TamboUI immediate-mode rendering |
| **M4 — CLI + Config** | **Done** | `GenerateCommand`, `InitCommand`, `PreviewCommand` all implemented. `ConfigLoader` with 4-level resolution. Exit codes defined |
| **M5 — Native + Release** | **Not started** | `springforge-native/` module does not yet exist. GraalVM reflect-config, release workflow, install script pending |
| **M6 — Integration Tests** | **Done** | `GeneratedCodeCompilationTest` (E2E pipeline), `CrudIntegrationTest` (Testcontainers PostgreSQL), `EdgeCaseIntegrationTest` |

### 15.2 What's Implemented

**Engine module (`springforge-engine`):**
- `EntityScanner` — scans source directories for `@Entity` files with fast pre-filter
- `JavaAstEntityParser` — AST parsing via JavaParser, circular ref detection
- `YamlEntityParser` — YAML entity definition parsing
- `TemplateRenderer` — Mustache template rendering
- `BatchGenerator` — parallel generation via Java 21 virtual threads
- `CodeFileWriter` — file writing with conflict strategy (skip/overwrite)
- 13 model records/enums: `EntityDescriptor`, `FieldDescriptor`, `GeneratedFile`, `GenerationConfig`, `GenerationReport`, `FileGenerationResult`, `Layer`, `RelationType`, `SpringVersion`, `MapperLib`, `ConflictStrategy`, `GenerationStatus`, `SpringNamespace`

**TUI module (`springforge-tui-screens`):**
- 7 stateless screen renderers (TamboUI `Frame`/`Rect` API)
- 6 immutable state records with builder methods
- `TuiRenderer` interface with callback interfaces
- `PlainCliRenderer` stdout fallback

**CLI module (`springforge-cli`):**
- `MainCommand` (Picocli entry point with global options)
- `GenerateCommand` (full flag set: entity selection, layer selection, output options)
- `InitCommand` (interactive config wizard)
- `PreviewCommand` (dry-run preview to stdout)
- `SpikeCommand` (Week 1 proof-of-concept)

**Config module (`springforge-config`):**
- `SpringForgeConfig` (YAML model with project, generation, naming sections)
- `ConfigLoader` (4-level resolution: CLI → explicit file → local file → defaults)
- `TelemetryManager` (opt-in anonymous usage data)

**Templates module (`springforge-templates`):**
- 11 Mustache templates: RequestDto, ResponseDto, MapstructMapper, ModelMapperConfig, Repository, Service, ServiceImpl, Controller, FileController, Liquibase, Flyway

### 15.3 What's Not Yet Implemented

| Feature | PRD Reference | Notes |
|---------|---------------|-------|
| GraalVM native binary packaging | G11, M5 | `springforge-native/` module not yet created |
| OpenAPI spec generation layer | G8, F-GEN-14 | CLI flag exists, but no `OPENAPI` entry in `Layer` enum |
| PATCH endpoint generation | F-GEN-16 | P1 — strategy not yet decided (OQ-4) |
| MyBatis mapper generation | F-GEN-18 | P1 — templates not yet created |
| Theme switching | F-TUI-12 | P1 |
| Mouse support | F-TUI-13 | P2 |
| Shell completion scripts | F-CLI-08 | P2 |
| JBang / GitHub Packages distribution | Section 10 | M5 |
| Test stubs generation | F-GEN-21, F-GEN-22 | P2 |
