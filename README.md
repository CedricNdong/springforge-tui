# SpringForge TUI

> *"The full power of IntelliJ Auto API Generator — in your terminal, for every developer, on every machine."*

[![Status](https://img.shields.io/badge/status-in%20development-yellow)](https://github.com/CedricNdong/springforge-tui)
[![Java](https://img.shields.io/badge/Java-21-blue)](https://openjdk.org/projects/jdk/21/)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)

---

## What is SpringForge TUI?

SpringForge TUI is a **terminal-first, IDE-agnostic CLI tool** that parses your Spring Boot `@Entity` classes and generates a complete, production-ready API stack in seconds.

No IntelliJ required. Works on VS Code, Neovim, remote servers, and CI/CD pipelines.

### What it generates

From a single `@Entity` class, SpringForge TUI generates:

- ✅ DTO (Request / Response / Patch)
- ✅ Mapper (MapStruct or ModelMapper)
- ✅ Repository (JPA or MyBatis)
- ✅ Service + ServiceImpl
- ✅ Controller (REST)
- ✅ Liquibase / Flyway migration script
- ✅ OpenAPI / Swagger 3 spec

---

## Quick Start

### Via JBang (zero install)

```bash
jbang springforge@CedricNdong/springforge-tui generate
```

### Via native binary

```bash
curl -L https://github.com/CedricNdong/springforge-tui/releases/latest/download/install.sh | bash
springforge generate
```

### Requirements (JVM mode)

- Java 21+

---

## Usage

```bash
# Interactive TUI mode (default)
springforge generate

# Generate all entities, all layers, non-interactive
springforge generate --all-entities --all --no-tui

# Dry run — preview without writing files
springforge generate --dry-run

# Initialize springforge.yml in current project
springforge init

# Preview generated code to stdout
springforge preview --entity User
```

### Common flags

| Flag | Description |
|---|---|
| `--entity <name>` | Target a specific entity |
| `--all-entities` | Process all detected entities |
| `--all` | Generate all layers |
| `--no-tui` | Non-interactive pipeline mode |
| `--dry-run` | Show what would be generated without writing |
| `--overwrite` | Overwrite existing files |
| `--output <dir>` | Custom output directory |
| `--spring-version <2\|3>` | Target Spring Boot version (default: 3) |
| `--verbose` | Detailed step-by-step logging |

---

## Configuration

Create a `springforge.yml` at your project root:

```yaml
basePackage: com.example.myapp
output:
  directory: src/main/java
generation:
  mapper: mapstruct       # mapstruct | modelmapper
  migration: liquibase    # liquibase | flyway | none
  openapi: yaml           # yaml | json | none
  onConflict: skip        # skip | overwrite
  lombok: true
  mybatis: false
```

Config resolution order (highest to lowest priority):
1. CLI flags
2. `springforge.yml` in project root
3. `~/.springforge/springforge.yml` (global)
4. Built-in defaults

---

## Documentation

| Document | Description |
|---|---|
| [PRD](docs/PRD.md) | Product requirements, features, milestones |
| [Technical Design](docs/TECHNICAL_SPEC.md) | Architecture, data models, implementation details |

---

## Development

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Run locally
./gradlew :springforge-cli:run --args="generate"

# Build native binary (requires GraalVM 25+)
./gradlew :springforge-cli:nativeCompile
```

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

---

## License

MIT © Cedric Ndong
