# SpringForge TUI — Product Requirements Document (PRD)

**Version:** 2.0  
**Date:** March 2026  
**Status:** Revised — Review corrections applied  
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
| G12 | Distribute via JBang and Maven Central |

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

### 6.1 Global Keyboard Shortcuts

| Key | Action |
|-----|--------|
| `Tab` / `Shift+Tab` | Move focus between panels |
| `↑` / `↓` | Navigate within a list |
| `Enter` | Confirm / select |
| `Space` | Toggle checkbox |
| `Esc` | Go back / cancel |
| `q` | Quit (with confirmation if generation in progress) |
| `?` | Show help overlay |
| `Ctrl+G` | Start generation from any screen |
| `Ctrl+P` | Open code preview panel |

### 6.2 Screen Flow

```
Launch
  └─► [S1] Splash / Scan
        └─► [S2] Entity Selection
              └─► [S3] Layer Configuration
                    └─► [S4] Code Preview
                          └─► [S5] Generation Progress
                                └─► [S6] Summary
```

Any screen → `Ctrl+G` → skips to S5 with current config  
Any screen → `?` → Help overlay (modal)  
Any screen → fatal error → Error screen

### 6.3 Screen Acceptance Criteria

**S1 — Splash / Scan**
- Shows ASCII logo and progress bar during project scan
- Auto-advances to S2 when scan completes
- Shows error screen if scan fails with actionable message

**S2 — Entity Selection**
- Lists all discovered `@Entity` classes with checkboxes
- Detail panel shows fields, types, annotations, and relationships for focused entity
- `A` / `N` selects or deselects all
- `Ctrl+F` opens fuzzy search / filter
- Proceeds only if ≥ 1 entity selected; shows inline warning otherwise

**S3 — Layer Configuration**
- Checkboxes for all generation layers
- Radio groups for: Spring version, mapper lib, migration tool, OpenAPI format, conflict strategy
- Footer shows live count: entities selected × layers × estimated file count

**S4 — Code Preview**
- File tree (left) — entity → generated files hierarchy
- Code preview (right) — syntax-highlighted content of selected file
- Preview renders from templates in-memory; no files written yet
- Scrollable preview panel

**S5 — Generation Progress**
- Overall progress bar (files written / total)
- Per-file status log: ✅ done / ⏳ in progress / ⚠️ skipped / ❌ error
- Auto-advances to S6 on completion
- Stays on screen on error with `[R]` retry / `[S]` skip options

**S6 — Summary**
- Counts: files created / skipped / errored
- Output directory tree
- Actions: open output dir, copy path, generate more, quit

---

## 7. Generation Engine Specification

### 7.1 Input Formats

| Format | How | Library |
|--------|-----|---------|
| Java `@Entity` class | AST parsing of `.java` source file | JavaParser |
| YAML entity definition | Schema parsing | SnakeYAML |

Both produce an identical internal `EntityDescriptor` model. See Tech Design Doc for schema.

### 7.2 Generated Files Per Entity

| Layer | File | Output path |
|-------|------|-------------|
| DTO Request | `{Entity}RequestDto.java` | `dto/` |
| DTO Response | `{Entity}ResponseDto.java` | `dto/` |
| Mapper | `{Entity}Mapper.java` | `mapper/` |
| Repository | `{Entity}Repository.java` | `repository/` |
| Service Interface | `{Entity}Service.java` | `service/` |
| Service Impl | `{Entity}ServiceImpl.java` | `service/impl/` |
| Controller | `{Entity}Controller.java` | `controller/` |
| File Upload Controller | `{Entity}FileController.java` | `controller/` |
| Liquibase migration | `V{timestamp}__create_{entity}.xml` | `resources/db/changelog/` |
| Flyway migration | `V{timestamp}__create_{entity}.sql` | `resources/db/migration/` |
| OpenAPI spec | `openapi.yaml` (one merged file for all entities) | `resources/` |

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
springforge:
  version: "1.0"

  project:
    basePackage: com.example
    srcDir: src/main/java
    resourceDir: src/main/resources
    springBootVersion: "3"        # "2" or "3"

  generation:
    mapperLib: mapstruct           # mapstruct | modelmapper
    migrationTool: liquibase       # liquibase | flyway | none
    openApiFormat: yaml            # yaml | json | none
    onConflict: skip               # skip | overwrite
    lombok: true
    mybatis: false

  naming:
    dtoSuffix: "Dto"
    serviceSuffix: "Service"
    controllerSuffix: "Controller"
    repositorySuffix: "Repository"
    apiPrefix: "/api"
    apiVersion: "v1"

  packages:
    dto: "${basePackage}.dto"
    mapper: "${basePackage}.mapper"
    repository: "${basePackage}.repository"
    service: "${basePackage}.service"
    controller: "${basePackage}.controller"
```

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

| ID | Priority | Requirement |
|----|----------|-------------|
| F-TUI-01 | P0 | Splash screen with project scan progress |
| F-TUI-02 | P0 | Entity selection screen with checkboxes and detail panel |
| F-TUI-03 | P0 | Layer configuration screen with all generation options |
| F-TUI-04 | P0 | Code preview screen with file tree and syntax-highlighted content |
| F-TUI-05 | P0 | Generation progress screen with per-file status |
| F-TUI-06 | P0 | Summary screen with output directory tree |
| F-TUI-07 | P0 | Help overlay from any screen via `?` |
| F-TUI-08 | P0 | Error screen with retry / skip options |
| F-TUI-09 | P0 | Keyboard-only navigation |
| F-TUI-10 | P0 | Auto-fallback to `--no-tui` mode when dumb terminal detected |
| F-TUI-11 | P1 | Fuzzy search / filter on entity list |
| F-TUI-12 | P1 | Theme switching (dark / light) via TCSS |
| F-TUI-13 | P2 | Mouse support for click navigation |

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
| NF-11 | Distribution | Available via `jbang` (zero install) and Maven Central |
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
| **M5 — Native + Release** | 1–2 weeks | Native binary < 100ms, published to Maven Central + JBang |

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
| JBang installs | > 1,000 | 3 months | JBang telemetry |
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

| # | Question | Why It Blocks |
|---|----------|---------------|
| OQ-1 | Does TamboUI + Picocli + JavaParser compile to a working GraalVM native binary? | Determines if G11 is achievable. **Resolved by Week 1 spike.** |
| OQ-2 | Which commit hash of TamboUI do we pin for M3? | Must be decided at M3 kickoff to avoid API drift mid-sprint |
| OQ-3 | Does SpringForge auto-detect Spring Boot version from `pom.xml` / `build.gradle`? | Affects CLI spec and config schema. Simplifies UX significantly if yes. |
| OQ-4 | What is the PATCH generation strategy? (null-check, JSON Merge Patch, Optional fields) | Required before implementing F-GEN-16. Decide before M2 ends. |

### 🟡 Can Decide During or After v1

| # | Question | Notes |
|---|----------|-------|
| OQ-5 | Windows native binary (not just WSL) — v1 or v2? | WSL covers most developer use cases; native Windows adds CI complexity |
| OQ-6 | How to handle pluralization for non-English entity names? | e.g., `Commande` → `commandes`? Default: append `s`, override via config |
| OQ-7 | MyBatis + JPA co-existence — is there a valid use case for both simultaneously? | If yes, generate separate mapper files; if no, make them mutually exclusive in config |
| OQ-8 | Custom template DX — validate at `springforge init` time or at generation time? | Init-time gives faster feedback; generation-time is simpler to implement |
| OQ-9 | File conflict resolution per-file in TUI (vs. global setting)? | Power user UX improvement; adds complexity to S3 screen |
