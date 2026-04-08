# Foodike Backend

A microservice-ready modular monolith backend for a food delivery app, built with Kotlin and Ktor. Designed as a reference architecture that demonstrates how to structure a backend so it can scale from a single deploy to independent microservices without rewriting business logic.

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

Each service module contains its own `domain/`, `infrastructure/`, `api/`, `di/`, and `migrations/` — fully self-contained. Service modules **never** import from each other. Cross-service communication happens through the event bus (async) or query ports (sync), both of which are interfaces that can be swapped from in-process calls to network calls when you extract a service.

## Tech Stack

| Layer | Tool |
|---|---|
| Language | Kotlin 2.x |
| Framework | Ktor 3.x (Netty) |
| Serialization | kotlinx.serialization |
| Database | PostgreSQL 16 + PostGIS |
| ORM | Exposed (DAO + DSL) |
| Migrations | Flyway (per-module) |
| Cache | Redis 7 (Lettuce) |
| DI | Koin 4 |
| Event Bus | In-process SharedFlow (swappable to RabbitMQ) |
| Auth | JWT (java-jwt) |
| Payments | Razorpay Java SDK |
| Push Notifications | Firebase Admin SDK |
| Storage | AWS S3 Kotlin SDK |
| Observability | Micrometer + Prometheus + Logback |
| Testing | Ktor testApplication + Kotest + MockK + Testcontainers |

## Getting Started

### Prerequisites

- JDK 21+
- Docker and Docker Compose
- A Razorpay test account (for payment flow)
- A Firebase project (for push notifications)

### Run Locally

```bash
# Clone the repo
git clone https://github.com/gautam84/foodike-backend.git
cd foodike-backend

# Start Postgres and Redis
docker-compose up -d postgres redis

# Copy environment template and fill in your keys
cp .env.example .env

# Run the application
./gradlew :app:run
```

The server starts at `http://localhost:8080`. Swagger docs are at `http://localhost:8080/docs`.

### Environment Variables

| Variable | Description | Required |
|---|---|---|
| `DB_URL` | Postgres JDBC URL | Yes |
| `DB_USER` | Postgres username | Yes |
| `DB_PASSWORD` | Postgres password | Yes |
| `REDIS_URL` | Redis connection URL | Yes |
| `JWT_SECRET` | Secret for signing JWTs | Yes |
| `JWT_ISSUER` | JWT issuer claim | Yes |
| `RAZORPAY_KEY` | Razorpay API key | For payments |
| `RAZORPAY_SECRET` | Razorpay API secret | For payments |
| `FIREBASE_CREDENTIALS` | Path to Firebase service account JSON | For notifications |
| `S3_BUCKET` | S3 bucket name for image uploads | For storage |
| `S3_REGION` | AWS region | For storage |

### Run Tests

```bash
# Unit tests
./gradlew :tests:test --tests "com.example.foodike.unit.*"

# Integration tests (requires Docker for Testcontainers)
./gradlew :tests:test --tests "com.example.foodike.integration.*"

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

Each service module follows the same internal structure:

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

**The critical rule:** service modules never depend on each other. This is enforced by Gradle. If `order-service` needs data from `restaurant-service`, it goes through a `MenuQueryPort` interface, not a direct import.

## Three Extraction Seams

This architecture is designed so that extracting any module into an independent microservice requires **zero business logic changes**. The three seams that make this possible:

### 1. Event Bus

Services communicate asynchronously through `EventBus`. Today it's backed by Kotlin `SharedFlow` (in-process). To extract, swap the Koin binding to `RabbitMqEventBus`.

```kotlin
// Today (monolith)
single<EventBus> { InProcessEventBus() }

// After extraction
single<EventBus> { RabbitMqEventBus(connectionFactory) }
```

### 2. Query Ports

When a service needs synchronous data from another service, it calls a port interface. Today the implementation is a direct in-process call. To extract, swap it to an HTTP client.

```kotlin
// Today (monolith) — same JVM, direct call
single<MenuQueryPort> { InProcessMenuAdapter(get()) }

// After extraction — HTTP call to restaurant-service
single<MenuQueryPort> { HttpMenuClient(httpClient, env("RESTAURANT_SERVICE_URL")) }
```

### 3. Per-Module Migrations

Each service has its own Flyway migration directory and history table. Today they all run against one Postgres. When extracted, each service points at its own database.

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

Full API documentation is available at `/docs` when running locally.

## License

This project is licensed under the MIT License. See [LICENSE.md](LICENSE.md) for details.

## Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting a PR.
