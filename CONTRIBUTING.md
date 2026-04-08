# Contributing to Foodike Backend

Thanks for your interest in contributing! This guide will help you understand the project's architecture rules and get your PR merged smoothly.

## Table of Contents

- [Getting Set Up](#getting-set-up)
- [Architecture Rules](#architecture-rules)
- [How to Pick an Issue](#how-to-pick-an-issue)
- [Making Changes](#making-changes)
- [Code Style](#code-style)
- [Writing Tests](#writing-tests)
- [Submitting a PR](#submitting-a-pr)
- [Where to Ask Questions](#where-to-ask-questions)

## Getting Set Up

1. **Fork and clone** the repository:
   ```bash
   git clone https://github.com/<your-username>/foodike-backend.git
   cd foodike-backend
   ```

2. **Install prerequisites:**
   - JDK 21 or later
   - Docker and Docker Compose
   - IntelliJ IDEA (recommended) or any Kotlin-capable IDE

3. **Start the infrastructure:**
   ```bash
   docker-compose up -d postgres redis
   ```

4. **Copy the environment file:**
   ```bash
   cp .env.example .env
   ```
   Fill in at least `DB_URL`, `DB_USER`, `DB_PASSWORD`, `REDIS_URL`, and `JWT_SECRET`. Payment and notification keys are only needed if you're working on those modules.

5. **Run the app:**
   ```bash
   ./gradlew :app:run
   ```

6. **Run the tests:**
   ```bash
   ./gradlew :tests:test
   ```

If everything passes, you're ready to contribute.

## Architecture Rules

These rules are non-negotiable. PRs that violate them will be requested to change before review.

### 1. Service modules never import from each other

This is the most important rule. `order-service` cannot import anything from `restaurant-service`, `user-service`, `payment-service`, or any other service module. Gradle enforces this at compile time.

**Why:** Each service module is a future microservice. Direct imports create coupling that would require a rewrite during extraction.

**How cross-service communication works:**

- **Async (fire-and-forget):** Publish an event through `EventBus`. Example: `OrderService` publishes `OrderPlaced`, and `PaymentService` consumes it.
- **Sync (need a response):** Define a query port interface in the calling service's `domain/port/` directory. Implement it as an `InProcess*Adapter` in `infrastructure/adapter/`. Example: `order-service` defines `MenuQueryPort` and calls it to fetch menu item prices at checkout.

If your feature needs data from another service and neither pattern exists for it yet, create a new query port. Don't take a shortcut by adding a cross-service Gradle dependency.

### 2. Domain has zero framework imports

Code inside any `domain/` directory must be pure Kotlin. No Ktor imports, no Exposed imports, no Redis imports, no third-party SDK imports. The only allowed dependencies are `shared/common` (value objects, exceptions) and `shared/events` (event contracts).

**Why:** The domain layer contains business logic. It should be testable without spinning up a server or database.

### 3. Events are the contract

When a significant state change happens (order placed, payment verified, status changed), the service must publish a domain event through `EventBus`. Other services react to events, they don't poll or call back.

Event classes live in `shared/events/`. If you need a new event:
1. Add the data class to the appropriate file in `shared/events/`
2. Publish it from the service where the state change occurs
3. Consume it in whichever services need to react

### 4. Every service owns its migrations

Database migrations live inside the service module at `migrations/db/migration/<service-name>/`. Don't put order-related tables in user-service's migration directory. Each service should only create and modify tables it owns.

### 5. DTOs stay in the API layer

Domain models and DTOs are separate. Domain models live in `domain/model/`. Request/response DTOs live in `api/dto/`. Mappers in `api/mapper/` convert between them. Routes should never return domain models directly.

## How to Pick an Issue

Check the [Issues](https://github.com/gautam84/foodike-backend/issues) tab. Issues are labeled:

| Label | Meaning |
|---|---|
| `good first issue` | Small, well-scoped, great for new contributors |
| `help wanted` | Open for anyone to pick up |
| `bug` | Something broken that needs fixing |
| `enhancement` | New feature or improvement |
| `documentation` | Docs, comments, or README improvements |
| `service:user` | Scoped to user-service |
| `service:restaurant` | Scoped to restaurant-service |
| `service:order` | Scoped to order-service |
| `service:payment` | Scoped to payment-service |
| `service:notification` | Scoped to notification-service |
| `service:tracking` | Scoped to tracking-service |
| `shared` | Affects shared modules |

**Before starting work**, comment on the issue to let others know you're picking it up. If an issue doesn't exist for what you want to work on, open one first so we can discuss the approach.

## Making Changes

1. **Create a branch** from `main`:
   ```bash
   git checkout -b feat/add-review-service
   ```

   Branch naming convention:
   - `feat/description` - new feature
   - `fix/description` - bug fix
   - `docs/description` - documentation
   - `refactor/description` - code restructuring
   - `test/description` - adding or fixing tests

2. **Follow the module structure.** If you're adding a new feature to an existing service, put files in the right directories:
   - Business logic -> `domain/service/`
   - New entity -> `domain/model/`
   - Database access -> `infrastructure/persistence/`
   - HTTP endpoint -> `api/routes/`
   - New event -> `shared/events/`

3. **If adding a new service module** (e.g., `review-service`):
   - Create it under `services/review-service/` following the standard structure
   - Add it to `settings.gradle.kts`
   - Create its Koin module and register it in `app/di/AppModule.kt`
   - Create its route registration in `app/plugins/Routing.kt`
   - Add its Flyway migration path in `app/Application.kt`
   - Make sure it only depends on `shared/` modules in `build.gradle.kts`

## Code Style

### Kotlin Conventions

- Follow the [official Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use `data class` for DTOs and value objects
- Use `sealed class`/`sealed interface` for type hierarchies (exceptions, events)
- Prefer `suspend fun` for any I/O operation
- Use meaningful names, `findActiveOrdersByRestaurant()` not `getOrders2()`

### Naming

| What | Convention | Example |
|---|---|---|
| Domain model | noun | `Order`, `MenuItem`, `Cart` |
| Repository interface | `*Repository` | `OrderRepository` |
| Repository impl | `*RepositoryImpl` | `OrderRepositoryImpl` |
| Service | `*Service` | `OrderService` |
| Query port | `*QueryPort` | `MenuQueryPort` |
| In-process adapter | `InProcess*Adapter` | `InProcessMenuAdapter` |
| Route function | `*Routes` | `orderRoutes(service)` |
| Koin module | `*Module` (val) | `val orderModule = module { }` |
| Request DTO | `*Request` | `PlaceOrderRequest` |
| Response DTO | `*Response` | `OrderResponse` |
| Event | past tense verb | `OrderPlaced`, `PaymentVerified` |
| Exposed table | `*Table` (plural) | `OrdersTable` |
| Migration file | `V{n}__{description}.sql` | `V1__create_orders.sql` |

### File Organization

One class per file for domain models, services, and repositories. DTOs can be grouped in one file if they're small and closely related (e.g., all request DTOs for a single route).

## Writing Tests

Every PR that adds or changes business logic should include tests.

### Unit Tests

For domain services. No database, no server, no network.

```kotlin
// tests/src/test/kotlin/com/example/foodike/unit/order/OrderServiceTest.kt
class OrderServiceTest : FunSpec({
    val orderRepo = mockk<OrderRepository>()
    val cartService = mockk<CartService>()
    val menuQuery = mockk<MenuQueryPort>()
    val eventBus = mockk<EventBus>(relaxed = true)

    val service = OrderService(orderRepo, cartService, menuQuery, eventBus)

    test("placeOrder throws when cart is empty") {
        coEvery { cartService.getCart(any()) } returns null

        shouldThrow<ValidationException> {
            service.placeOrder(UUID.randomUUID(), UUID.randomUUID())
        }
    }

    test("placeOrder publishes OrderPlaced event") {
        // ... setup mocks ...
        service.placeOrder(userId, addressId)

        coVerify { eventBus.publish(match { it.type == "order.placed" }) }
    }
})
```

### Integration Tests

For routes and database operations. Use Ktor's `testApplication` and Testcontainers for Postgres.

```kotlin
// tests/src/test/kotlin/com/example/foodike/integration/order/OrderRoutesTest.kt
class OrderRoutesTest : FunSpec({
    test("POST /orders returns 201 with valid cart") {
        testApplication {
            application { /* configure test modules */ }

            val response = client.post("/orders") {
                header("Authorization", "Bearer $testToken")
                contentType(ContentType.Application.Json)
                setBody(PlaceOrderRequest(addressId = testAddressId))
            }

            response.status shouldBe HttpStatusCode.Created
        }
    }
})
```

### What to Test

| Layer | What to test | How |
|---|---|---|
| Domain services | Business rules, state transitions, validation | Unit test with MockK |
| Domain models | State machine transitions (e.g., `OrderStatus`) | Unit test, no mocks |
| Repositories | SQL correctness, constraints | Integration test with Testcontainers |
| Routes | HTTP status codes, request validation, auth | Integration test with `testApplication` |
| Event flow | Events published on state changes | Unit test, verify `eventBus.publish()` |

### Running Tests

```bash
# All tests
./gradlew :tests:test

# Only unit tests
./gradlew :tests:test --tests "com.example.foodike.unit.*"

# Only integration tests
./gradlew :tests:test --tests "com.example.foodike.integration.*"

# Specific service tests
./gradlew :tests:test --tests "*.order.*"
```

## Submitting a PR

1. **Make sure all tests pass** locally before pushing.

2. **Push your branch** and open a PR against `main`.

3. **PR title** should be descriptive:
   - `feat(order): add coupon validation with max usage limit`
   - `fix(payment): handle Razorpay webhook signature verification failure`
   - `docs: add deployment section to README`

4. **PR description** should include:
   - What the change does
   - Which service module(s) it touches
   - How to test it manually (if applicable)
   - Link to the related issue

5. **Keep PRs focused.** One feature or fix per PR. If your change touches multiple services, that's fine (shared events often require this), but don't bundle unrelated changes.

6. **Respond to review feedback** within a reasonable time. If you disagree with a suggestion, explain why, discussion is welcome.

## Where to Ask Questions

- **GitHub Issues** - for bugs, feature requests, and architecture questions
- **GitHub Discussions** - for general questions and ideas
- **PR comments** - for questions about specific code during review

Thanks for contributing!
