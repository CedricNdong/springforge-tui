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

From your `@Entity` classes, SpringForge generates:

- DTO (Request + Response) with proper relationship flattening
- Mapper (MapStruct or ModelMapper)
- Repository (Spring Data JPA)
- Service + ServiceImpl with full CRUD
- REST Controller with pagination
- Liquibase / Flyway migration script

Handles `@ManyToOne`, `@OneToMany`, `@ManyToMany`, `@OneToOne`, circular references, Lombok detection, and Spring Boot 2/3 namespace switching.

---

## Installation

### Option 1 — Native binary (recommended)

Download the latest release for your platform:

```bash
# Linux (x86_64)
curl -L https://github.com/CedricNdong/springforge-tui/releases/latest/download/springforge-linux-x86_64 -o springforge
chmod +x springforge
sudo mv springforge /usr/local/bin/

# macOS (Apple Silicon)
curl -L https://github.com/CedricNdong/springforge-tui/releases/latest/download/springforge-macos-aarch64 -o springforge
chmod +x springforge
sudo mv springforge /usr/local/bin/
```

Or use the install script:

```bash
curl -L https://github.com/CedricNdong/springforge-tui/releases/latest/download/install.sh | bash
```

### Option 2 — Fat JAR (requires Java 21+)

Download `springforge-tui-*-all.jar` from the [releases page](https://github.com/CedricNdong/springforge-tui/releases) and run:

```bash
java -jar springforge-tui-*-all.jar generate --help
```

---

## Quick Start

1. Navigate to your Spring Boot project root (where your `@Entity` classes are):

```bash
cd /path/to/your/spring-boot-project
```

2. Generate all API layers for all detected entities:

```bash
# With native binary
springforge generate --all

# With fat JAR
java -jar springforge-tui-*-all.jar generate --all
```

SpringForge auto-scans `src/main/java/` for `@Entity` classes and generates DTOs, Mappers, Repositories, Services, and Controllers.

3. Preview what would be generated without writing files:

```bash
springforge generate --all --dry-run
```

### Targeting specific entities

```bash
# Single entity file
springforge generate --entity src/main/java/com/example/model/User.java --all

# Multiple entity files
springforge generate --entities User.java Product.java Order.java --all

# Scan a specific directory
springforge generate --dir src/main/java/com/example/model --all
```

---

## Usage

```bash
# Generate all layers for all entities (auto-scan src/main/java)
springforge generate --all

# Generate only DTOs and Mappers
springforge generate --dto --mapper

# Generate with ModelMapper instead of MapStruct
springforge generate --all --mapper-lib modelmapper

# Target Spring Boot 2.x (uses javax.* namespace)
springforge generate --all --spring-version 2

# Generate to a custom output directory
springforge generate --all --output /tmp/generated

# Overwrite existing files (default: skip)
springforge generate --all --overwrite

# Dry run — preview without writing files
springforge generate --all --dry-run
```

### All flags

| Flag | Description |
|---|---|
| `-e, --entity <file>` | Single Java entity file |
| `-E, --entities <files...>` | Multiple Java entity files |
| `-d, --dir <dir>` | Scan directory for `@Entity` classes |
| `--all-entities` | Auto-discover all `@Entity` classes in `src/` |
| `--all` | Generate all layers |
| `--dto` | Generate DTO classes only |
| `--mapper` | Generate mapper only |
| `--repository` | Generate repository only |
| `--service` | Generate service + impl only |
| `--controller` | Generate controller only |
| `--migration` | Generate database migration only |
| `--dry-run` | Show what would be generated without writing |
| `--overwrite` | Overwrite existing files (default: skip) |
| `-o, --output <dir>` | Override output base directory |
| `--spring-version <2\|3>` | Target Spring Boot version (default: 3) |
| `--mapper-lib <lib>` | Mapper library: `mapstruct` or `modelmapper` |
| `--db-migration <tool>` | Migration tool: `liquibase` or `flyway` |
| `--no-tui` | Non-interactive pipeline mode |

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

### Build from source

```bash
# Build all modules
./gradlew build

# Run unit tests
./gradlew test

# Run integration tests (requires Docker for Testcontainers)
./gradlew :springforge-integration-tests:integrationTest

# Run locally via Gradle
./gradlew :springforge-cli:run --args="generate --help"
```

### Build distributable artifacts

```bash
# Fat JAR — single JAR with all dependencies
./gradlew :springforge-cli:fatJar
# Output: springforge-cli/build/libs/springforge-cli-*-all.jar

# Native binary (requires GraalVM 21+ with native-image)
./gradlew :springforge-cli:nativeCompile
# Output: springforge-cli/build/native/nativeCompile/springforge
```

### Module structure

```
springforge-tui/
├── springforge-cli/          # Picocli commands, entry points
├── springforge-tui-screens/  # TUI screen implementations
├── springforge-engine/       # Core: scanner, parser, renderer, writer
├── springforge-templates/    # Mustache templates (built-in)
├── springforge-config/       # springforge.yml parsing
├── springforge-native/       # GraalVM native-image config
└── springforge-integration-tests/  # E2E tests with Testcontainers
```

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

---

## License

MIT © Cedric Ndong
