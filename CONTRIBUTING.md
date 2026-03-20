# Contributing to SpringForge TUI

## Branching Strategy

```
main          → always releasable, protected
develop       → integration branch
feature/xxx   → new features (from develop)
fix/xxx       → bug fixes (from develop)
spike/xxx     → technical spikes
chore/xxx     → maintenance, docs, config
```

## Commit Convention (Conventional Commits)

```
type(scope): short description

Types  : feat, fix, chore, docs, test, refactor, spike
Scopes : cli, tui, engine, config, templates, native, deps
```

## Definition of Done

A feature is done when:
- [ ] Code written and self-reviewed
- [ ] Unit tests: happy path + top 3 error cases
- [ ] Integration test: generated code compiles against a real Spring Boot 3.x project
- [ ] Documented in README or inline help
- [ ] No new compiler warnings introduced
- [ ] PR merged to `develop`
