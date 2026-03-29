# Contributing to SpringForge TUI

Thank you for your interest in contributing! This guide will help you get started.

---

## Getting Started

1. **Fork** the repository
2. **Clone** your fork
   ```bash
   git clone https://github.com/<your-username>/springforge-tui.git
   cd springforge-tui
   ```
3. **Build** the project
   ```bash
   ./gradlew build
   ```

---

## Branching Strategy (Gitflow)

```
main          → always releasable, protected
develop       → integration branch
feature/xxx   → new features (branched from develop)
fix/xxx       → bug fixes (branched from develop)
spike/xxx     → technical spikes
chore/xxx     → maintenance, docs, config
```

**Important:** All PRs must target `develop`. PRs targeting `main` directly will be rejected by CI. Only `develop` is merged into `main` for releases.

```
feature/xxx ──PR──► develop ──PR──► main
fix/xxx     ──PR──► develop ──PR──► main
```

---

## Commit Convention

We use [Conventional Commits](https://www.conventionalcommits.org/):

```
type(scope): short description

Types  : feat, fix, chore, docs, test, refactor, spike
Scopes : cli, tui, engine, config, templates, native, deps
```

Examples:
```
feat(engine): add YamlEntityParser for YAML-based entity definitions
fix(tui): handle dumb terminal detection before TamboUI init
chore(deps): pin TamboUI to commit abc1234
test(engine): add unit tests for circular relationship DTO flattening
docs: update README with tech stack section
```

---

## Development Workflow

1. Create your branch from `develop`:
   ```bash
   git checkout develop
   git pull origin develop
   git checkout -b feature/my-feature
   ```

2. Make your changes

3. Run tests:
   ```bash
   # Unit tests
   ./gradlew test

   # Integration tests (requires Docker)
   ./gradlew :springforge-integration-tests:integrationTest
   ```

4. Commit and push:
   ```bash
   git commit -m "feat(scope): description"
   git push origin feature/my-feature
   ```

5. Open a Pull Request targeting `develop`

---

## Definition of Done

A feature is done when:

- [ ] Code written and self-reviewed
- [ ] Unit tests: happy path + top 3 error cases
- [ ] Integration test: generated code compiles against a real Spring Boot 3.x project
- [ ] No new compiler warnings introduced
- [ ] Checkstyle and SpotBugs pass
- [ ] PR reviewed and merged to `develop`

---

## Code Style

- **Google Java Style Guide** with 120 character line limit
- Enforced by Checkstyle in CI
- Use `@DisplayName` for all test methods
- Prefer Java records for immutable data
- Never return `null` from public methods — use `Optional<T>`

---

## Reporting Issues

If you find a bug or have a feature request, please [open an issue](https://github.com/CedricNdong/springforge-tui/issues) with:

- A clear title
- Steps to reproduce (for bugs)
- Expected vs actual behavior
- Your environment (Java version, OS)
