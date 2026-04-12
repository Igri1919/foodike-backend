# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Run the application (starts on http://localhost:8080)
./gradlew :app:run

# Compile all modules
./gradlew compileKotlin

# Run all tests
./gradlew test

# Run a specific test class
./gradlew :tests:test --tests "com.example.foodike.integration.ApplicationModuleTest"

# Run a specific test method
./gradlew :tests:test --tests "com.example.foodike.integration.ApplicationModuleTest.otpAuthFlowIssuesRotatesAndRevokesTokens"
```

## Architecture

Modular monolith — Kotlin/Ktor with hexagonal (ports & adapters) per service. Each service module is shaped as a future microservice with explicit extraction seams.

### Module Layout

- **`app/`** — Composition root. Ktor entry point, plugin installation (`plugins/`), Koin wiring. `Application.kt` calls `configureInfrastructure()` (DB, Redis, Koin) then `configureAuthentication()` (JWT) then routing/middleware.
- **`shared/`** — Cross-cutting concerns. `common` (exceptions, value objects), `events` (EventBus interface), `auth` (JWT generation/validation, `UserPrincipal`), `persistence` (Exposed `dbQuery`, `DatabaseFactory`).
- **`services/`** — Business modules. Each follows `domain/` (model, port, service), `infrastructure/` (persistence, auth adapters), `api/` (routes, DTOs), `di/` (Koin module).
- **`tests/`** — Integration tests using Ktor `testApplication`. Tests load `application.yaml` via `YamlConfig` in the `environment` block (no `application { module() }` — the YAML auto-triggers the module).

### Critical Dependency Rule

Service modules depend only on `shared/` — **never on each other**. Cross-service data flows through port interfaces (in domain layer) or the EventBus.

### Configuration

Single source of truth: `app/src/main/resources/application.yaml` with `${ENV_VAR:default}` syntax. Environment variables override defaults. No `.env` file in the runtime chain. `.env` is gitignored.

### DI Pattern

Koin is installed in `configureInfrastructure()`. Infrastructure singletons (database, Redis, JWT, auth properties) are registered in `app/plugins/Authentication.kt`. Service-level bindings live in each service's `di/` module (e.g., `userModule`). Routes use `inject<T>()` from `koin-ktor`.

### Auth Flow

OTP-based phone auth + Google sign-in (not yet wired). `AuthService` orchestrates OTP send/verify, token issuance, refresh rotation, and logout. OTP challenges and rate limits use Redis with in-memory fallback. Refresh tokens are BCrypt-hashed in the database; OTPs are SHA-256 hashed in Redis/memory. Protected routes use Ktor's `authenticate("auth-jwt")` block.

### Redis Fallback

If Redis is unavailable at startup, the app logs a warning and uses `InMemory*Store` implementations for OTP challenges and rate limiting. The decision is made once at boot in `initializeRedis()`.

### EventBus

`InProcessEventBus` (SharedFlow-based) today. Designed to swap to a broker-backed implementation for microservice extraction.
