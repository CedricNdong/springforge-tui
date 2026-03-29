# CLAUDE.md — SpringForge TUI

> Instructions for Claude Code. Read this file before writing any code.

---

## Project Context

**SpringForge TUI** is a terminal-first, IDE-agnostic CLI tool that:
- Parses existing Java `@Entity` classes (via AST) or YAML entity definitions
- Generates a complete Spring Boot API stack (DTO, Mapper, Repository, Service, Controller)
- Exposes an interactive TUI powered by TamboUI
- Compiles to a GraalVM native binary (< 100ms startup)
- Runs headlessly in CI/CD pipelines via `--no-tui` mode

Reference docs:
- PRD (what & why): `docs/PRD.md`
- Technical Design (how): `docs/TECHNICAL_SPEC.md`
- TamboUI API Doc as pdfs in order: `docs/api/tamboui-Doc- 0.2.0-SNAPSHOT API`

---

## Technology Stack

| Layer | Technology           | Version |
|---|----------------------|---|
| Language | Java                 | 21 LTS |
| CLI Framework | Picocli              | 4.7+ |
| TUI Framework | TamboUI              | pinned commit (see Tech Design) |
| AST Parser | JavaParser           | 3.25+ |
| YAML Parser | SnakeYAML            | 2.x |
| Template Engine | Mustache.java        | 0.9.x |
| Config Parsing | Jackson YAML         | 2.x |
| Build Tool | Gradle (Groovy DSL)  | 8.x |
| Native Build | GraalVM native-image | 25+ |
| Testing | JUnit 5 + AssertJ    | Latest |
| Integration Testing | Testcontainers       | 1.20.x |

---

## Module Structure

```
springforge-tui/
├── springforge-cli/          ← Picocli commands, entry points
├── springforge-tui-screens/  ← TamboUI screen implementations + TuiRenderer interface
├── springforge-engine/       ← Core generation logic (scanner, parser, renderer, writer)
├── springforge-templates/    ← Mustache templates (built-in)
├── springforge-config/       ← springforge.yml parsing + config resolution
├── springforge-native/       ← GraalVM native-image config (PLANNED — not yet created)
└── springforge-integration-tests/ ← E2E tests with Testcontainers PostgreSQL
```

See `docs/TECHNICAL_SPEC.md` Section 3 for full module structure with package details.

---

## Key Abstractions

### TuiRenderer Interface
All TUI calls MUST go through `TuiRenderer` — never call TamboUI directly from business logic.
```java
// CORRECT
tuiRenderer.showEntitySelection(state, callbacks);

// WRONG — couples business logic to TamboUI
new EntitySelectionScreen(tamboApp).show(entities);
```

Two implementations:
- `TamboUiRenderer` — full TUI (default)
- `PlainCliRenderer` — stdout fallback (`--no-tui` / dumb terminal)

### Generation Pipeline
```
EntityScanner → EntityParser → TemplateRenderer → OutputResolver → ConflictChecker → FileWriter
```
Each step is a separate class. Do not merge responsibilities.

### Data Models (Java Records)
Use records for immutable data models:
- `EntityDescriptor` — parsed entity metadata
- `FieldDescriptor` — field metadata
- `GenerationConfig` — user's generation options
- `GeneratedFile` — in-memory generated file before write

See `docs/TECHNICAL_SPEC.md` Section 4 for complete field definitions.

---

## Coding Conventions

### Language
- **Code, variables, methods, classes:** English only
- **Comments:** English
- **Commit messages:** English (Conventional Commits)
- **Log messages:** English

### Java Style
- Follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- Max method length: 30 lines — extract if longer
- No magic strings — use constants or enums
- Prefer Java records for immutable data
- Use sealed interfaces for sum types (e.g., parse result: success | error)
- Use `var` only when the type is obvious from the right-hand side
- No raw types — always parameterize generics

### Null Safety
- Never return `null` from public methods — use `Optional<T>` or sealed result types
- Never pass `null` as a method argument — use overloads or builders

### Logging
- Use SLF4J — never `System.out.println` in production code
- `--verbose` output goes to stderr, not stdout
- Log format: `[HH:mm:ss.SSS] message`

### Templates (Mustache)
- Templates live in `springforge-templates/src/main/resources/templates/`
- No logic in templates — all conditionals resolved in `TemplateContext` builders
- One template per generated file type (e.g., `controller.mustache`, `service.mustache`)

---

## Conventions Git

```
type(scope): short description

Types  : feat, fix, chore, docs, test, refactor, spike
Scopes : cli, tui, engine, config, templates, native, deps

Examples:
  feat(engine): add YamlEntityParser for YAML-based entity definitions
  fix(tui): handle dumb terminal detection before TamboUI init
  chore(deps): pin TamboUI to commit abc1234
  test(engine): add unit tests for circular relationship DTO flattening
```

Branches:
```
main          → always releasable
develop       → integration branch
feature/xxx   → new features
fix/xxx       → bug fixes
spike/xxx     → technical spikes (Week 1 spike = spike/graalvm-tamboui)
chore/xxx     → maintenance
```

---

## Testing Rules

- Every public method MUST have at minimum: happy path test + top 3 error cases
- Integration tests MUST compile generated code against a real Spring Boot 3.x project
- Test matrix defined in `docs/PRD.md` Section 12.3 — respect coverage tiers
- Test class naming: `[ClassUnderTest]Test.java`
- Use `@DisplayName` for readable test descriptions

```java
@Test
@DisplayName("should skip entity when @Entity annotation is missing")
void shouldSkipEntityWithoutAnnotation() { ... }
```

---

## ⚠️ Critical Rules — Never Break These

1. **TamboUI is NEVER called directly from engine or CLI code** — always through `TuiRenderer`
2. **Never use `LATEST` for TamboUI version** — always a pinned commit hash
3. **Generated code must never execute user code** — AST parsing only, no reflection, no classloading
4. **Output paths are always resolved relative to project root** — reject `../` traversal
5. **No network calls in v1** — all generation is fully offline
6. **Never add a dependency without updating this file and `docs/TECHNICAL_SPEC.md`**
7. **Week 1 spike (GraalVM + TamboUI feasibility) must pass before any M1 work begins**

---

## Out of Scope for v1 — Do Not Implement

- Code diffing / merge strategy (planned v2)
- Authentication / security layer generation
- Frontend code generation
- Database introspection from live DB
- Windows native binary (WSL only)
- Quarkus / Micronaut support

If a task would implement any of the above, stop and ask for clarification.
