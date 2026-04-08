# Foodike Backend

A microservice-ready modular monolith backend for a food delivery app, built with Kotlin and Ktor. It is being developed as a reference architecture that can scale from a single deploy to independent microservices without rewriting business logic.

## Why This Exists

Most food delivery backend tutorials are either too simple (single-file CRUD) or jump straight to microservices (overkill for 99% of projects). This project sits in the sweet spot: a production-grade modular monolith where every module is shaped like a future microservice, with explicit extraction seams documented and enforced by Gradle.

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                   Single JVM Process                │
│                                                     │
│  ┌─────────────────────────────────────────────┐    │
│  │              shared/                        │    │
│  │   common · events · auth · persistence      │    │
│  └─────────────────────────────────────────────┘    │
│                                                     │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐            │
│  │  user    │ │restaurant│ │  order   │            │
│  │ service  │ │ service  │ │ service  │            │
│  └──────────┘ └──────────┘ └──────────┘            │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐            │
│  │ payment  │ │ notific. │ │ tracking │            │
│  │ service  │ │ service  │ │ service  │            │
│  └──────────┘ └──────────┘ └──────────┘            │
│         │            │            │                 │
│  ┌─────────────────────────────────────────────┐    │
│  │         EventBus (SharedFlow)               │    │
│  └─────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────┘
```

The target architecture is for each service module to contain its own `domain/`, `infrastructure/`, `api/`, `di/`, and `migrations/`. The current repository has a lighter scaffold in place and is moving toward that structure incrementally.

## Current Status

Implemented today:
- Multi-module Gradle build
- Shared `common`, `events`, `auth`, and `persistence` modules
- Service module boundaries for user, restaurant, order, payment, notification, and tracking
- Ktor application shell in `app/`
- Placeholder routes and minimal domain models
- Basic integration test for app startup

Planned but not implemented yet:
- PostgreSQL/PostGIS setup
- Redis integration
- Flyway migrations
- Razorpay, Firebase, and S3 integrations
- Swagger/OpenAPI docs
- Micrometer/Prometheus metrics
- Full domain services, repositories, DTOs, and production flows
- Kotest, MockK, and Testcontainers-based test suite

## Tech Stack

| Layer | Tool |
|---|---|
| Language | Kotlin 2.x |
| Framework | Ktor 3.x (Netty) |
| Serialization | kotlinx.serialization |
| Database | H2 today, PostgreSQL/PostGIS planned |
| ORM | Exposed DSL |
| Migrations | Planned |
| Cache | Planned |
| DI | Koin 4 |
| Event Bus | In-process SharedFlow (swappable to RabbitMQ) |
| Auth | JWT (java-jwt) |
| Payments | Planned |
| Push Notifications | Planned |
| Storage | Planned |
| Observability | Logback today, broader observability planned |
| Testing | Ktor `testApplication` today, broader test stack planned |

## Getting Started

### Prerequisites

- JDK 17+
- No external infrastructure is required for the current scaffold

### Run Locally

```bash
# Clone the repo
git clone https://github.com/gautam84/foodike-backend.git
cd foodike-backend

# Run the application
./gradlew :app:run
```

The server starts at `http://localhost:8080`.

### Environment Variables

The current scaffold only uses application config from `app/src/main/resources/application.yaml`.

Planned environment variables for future infrastructure:

| Variable | Description | Required |
|---|---|---|
| `DB_URL` | Postgres JDBC URL | Planned |
| `DB_USER` | Postgres username | Planned |
| `DB_PASSWORD` | Postgres password | Planned |
| `REDIS_URL` | Redis connection URL | Planned |
| `JWT_SECRET` | Secret for signing JWTs | Planned |
| `JWT_ISSUER` | JWT issuer claim | Planned |
| `RAZORPAY_KEY` | Razorpay API key | For payments |
| `RAZORPAY_SECRET` | Razorpay API secret | For payments |
| `FIREBASE_CREDENTIALS` | Path to Firebase service account JSON | For notifications |
| `S3_BUCKET` | S3 bucket name for image uploads | For storage |
| `S3_REGION` | AWS region | For storage |

### Run Tests

```bash
# Unit tests
./gradlew :tests:test --tests "com.example.foodike.unit.*"

# All tests
./gradlew :tests:test
```

## Project Structure

```
foodike-backend/
├── shared/
│   ├── common/          # Value objects, exceptions, utilities
│   ├── events/          # Event contracts + EventBus interface
│   ├── auth/            # JWT validation, shared across all modules
│   └── persistence/     # Database factory, query helpers
│
├── services/
│   ├── user-service/          # Auth (OTP), profiles, addresses
│   ├── restaurant-service/    # Restaurants, menus, search
│   ├── order-service/         # Cart, orders, coupons
│   ├── payment-service/       # Razorpay integration
│   ├── notification-service/  # FCM, SMS, email
│   └── tracking-service/      # WebSocket order tracking
│
├── app/                 # Ktor entry point, plugin config, Koin wiring
└── tests/               # Unit + integration tests
```

Target per-service structure:

```
services/xyz-service/
├── domain/
│   ├── model/           # Entities and value objects
│   ├── repository/      # Interfaces (ports)
│   ├── service/         # Business logic
│   └── port/            # Cross-service query interfaces (if needed)
├── infrastructure/
│   ├── persistence/
│   │   ├── tables/      # Exposed table definitions
│   │   └── *Impl.kt    # Repository implementations
│   ├── adapter/         # In-process adapters for query ports
│   └── events/          # Event publishers and consumers
├── api/
│   ├── routes/          # Ktor route definitions
│   ├── dto/             # Request/response objects
│   └── mapper/          # Domain ↔ DTO mappers
├── di/
│   └── XyzModule.kt    # Koin module
└── migrations/
    └── db/migration/    # Flyway SQL files
```

## Module Dependency Rules

```
shared/common        ← no dependencies
shared/events        ← common
shared/auth          ← common
shared/persistence   ← common

services/*           ← shared modules only (NEVER other services)

app                  ← all services (composition root)
```

**The critical rule:** service modules should never depend on each other. The project is structured around that rule, and module dependencies should continue to preserve it. If `order-service` needs data from `restaurant-service`, it should go through a query port interface rather than a direct import.

## Three Extraction Seams

This architecture is intended so that extracting any module into an independent microservice can happen with minimal business-logic changes. These seams are part of the target design and are only partially implemented today:

### 1. Event Bus

Services communicate asynchronously through `EventBus`. Today the repo includes an in-process event bus. A broker-backed event bus is planned for extraction scenarios.

```kotlin
// Today (monolith)
single<EventBus> { InProcessEventBus() }

// Planned after extraction
single<EventBus> { RabbitMqEventBus(connectionFactory) }
```

### 2. Query Ports

When a service needs synchronous data from another service, it should call a port interface. The concrete in-process and HTTP adapters shown below are target examples and are not fully implemented yet.

```kotlin
// Target monolith form
single<MenuQueryPort> { InProcessMenuAdapter(get()) }

// Planned extraction form
single<MenuQueryPort> { HttpMenuClient(httpClient, env("RESTAURANT_SERVICE_URL")) }
```

### 3. Per-Module Migrations

Each service is intended to own its own migrations. Flyway integration is planned and not wired into the current scaffold yet.

### Extraction Checklist

To extract any service into an independent microservice:

1. Copy `services/xyz-service/` into its own Gradle project
2. Add its own `Application.kt` and `Dockerfile`
3. Swap `InProcessEventBus` → `RabbitMqEventBus`
4. Swap `InProcess*Adapter` → `Http*Client` for query ports
5. Point Flyway at a dedicated database
6. Add to API gateway route config
7. Remove from monolith's `settings.gradle.kts` and `AppModule.kt`

## API Overview

### Auth
- `POST /auth/send-otp` — send OTP to phone
- `POST /auth/verify-otp` — verify and get JWT pair
- `POST /auth/refresh` — refresh access token

### Users
- `GET /users/me` — current profile
- `PUT /users/me` — update profile
- `GET /users/me/addresses` — list addresses
- `POST /users/me/addresses` — add address
- `DELETE /users/me/addresses/{id}` — remove address

### Restaurants
- `GET /restaurants` — list (paginated, filterable)
- `GET /restaurants/nearby?lat=&lng=&radius=` — geo query
- `GET /restaurants/{id}` — detail
- `GET /restaurants/{id}/menu` — grouped menu

### Cart
- `GET /cart` — current cart
- `POST /cart/items` — add item
- `PUT /cart/items/{id}` — update quantity
- `DELETE /cart/items/{id}` — remove item
- `DELETE /cart` — clear
- `POST /cart/coupon` — apply coupon

### Orders
- `POST /orders` — place order
- `GET /orders` — list (paginated)
- `GET /orders/{id}` — detail
- `PATCH /orders/{id}/status` — update status
- `WS /track/{orderId}` — live tracking

### Payments
- `POST /payments/initiate` — create Razorpay order
- `POST /payments/verify` — verify payment signature

### Search
- `GET /search?q=&type=` — full-text search

The endpoint list above reflects the target API surface. The current implementation only exposes a small subset of placeholder routes while the modules are being built out.

## License

This project is licensed under the MIT License. See [LICENSE.md](LICENSE.md) for details.

## Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting a PR.
