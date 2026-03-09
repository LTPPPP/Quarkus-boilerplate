# Quarkus Enterprise Boilerplate

Production-ready **Quarkus 3.x** + **Java 21** starter template with Kafka event handling, multi-tenancy, and gRPC — clone, customize, and ship.

## What's Included

| Module | Features |
|--------|----------|
| **Kafka Event Handling** | BaseEvent envelope, typed producers/consumers, idempotency (Redis), DLQ with retry, transactional outbox pattern |
| **Multi-Tenancy** | Schema-per-tenant + row-level fallback, dynamic schema routing, tenant provisioning, quota enforcement, tenant-aware cache |
| **gRPC** | Unary/streaming/bidi RPCs, auth + tenant + logging interceptors, REST-to-gRPC gateway, inter-service clients |
| **Observability** | OpenTelemetry, Jaeger tracing, Prometheus metrics, Grafana dashboards |
| **Security** | JWT (SmallRye), RBAC, encrypted tenant secrets |
| **Infrastructure** | Docker Compose (full stack), Dockerfiles (JVM + native), Maven wrapper, Flyway migrations |

## Architecture

```
                    ┌─────────────────────────────────────────────────────────────────┐
                    │                        LOAD BALANCER / API GATEWAY              │
                    └──────────┬──────────────────────────────────┬───────────────────┘
                               │ REST (8080)                      │ gRPC (9000)
                    ┌──────────▼──────────────────────────────────▼───────────────────┐
                    │                      APPLICATION CLUSTER                         │
                    │                                                                  │
                    │  ┌─────────────┐  ┌─────────────┐  ┌────────────────────────┐   │
                    │  │  JAX-RS     │  │  gRPC       │  │  gRPC Gateway          │   │
                    │  │  REST API   │  │  Services   │  │  (REST → gRPC Bridge)  │   │
                    │  └──────┬──────┘  └──────┬──────┘  └───────────┬────────────┘   │
                    │         │                 │                      │                │
                    │  ┌──────▼─────────────────▼──────────────────────▼────────────┐  │
                    │  │                CROSS-CUTTING LAYER                          │  │
                    │  │  TenantFilter → TenantContext → AuthInterceptor → Logging  │  │
                    │  │  QuotaEnforcement → DistributedTracing (OpenTelemetry)      │  │
                    │  └──────────────────────────┬────────────────────────────────┘  │
                    │                              │                                   │
                    │  ┌───────────────────────────▼───────────────────────────────┐  │
                    │  │                   SERVICE LAYER                            │  │
                    │  │  UserService │ OrderService │ PaymentService │ TenantSvc   │  │
                    │  └──────┬───────────────┬──────────────┬────────────┬────────┘  │
                    │         │               │              │            │            │
                    │  ┌──────▼───┐  ┌────────▼───┐  ┌──────▼─────┐  ┌──▼────────┐  │
                    │  │Repository│  │  Event     │  │  gRPC      │  │  Tenant   │  │
                    │  │ (Panache)│  │  Producers │  │  Clients   │  │  Aware    │  │
                    │  └────┬─────┘  └─────┬─────┘  └─────┬──────┘  └────┬─────┘  │
                    └───────┼──────────────┼──────────────┼───────────────┼────────┘
                            │              │              │               │
                 ┌──────────▼──┐  ┌────────▼────────┐  ┌─▼──┐  ┌────────▼────────┐
                 │ PostgreSQL  │  │  Apache Kafka    │  │gRPC│  │     Redis        │
                 │ (per-schema │  │  user-events     │  │Svc │  │  (Cache/Idemp.   │
                 │  tenancy)   │  │  order-events    │  └────┘  │   /Quota/Locks)  │
                 └─────────────┘  │  payment-events  │          └──────────────────┘
                                  │  dead-letter-q   │
                                  └──────────────────┘

    ┌──────────────────── OBSERVABILITY STACK ─────────────────────┐
    │  Jaeger (Tracing)  │  Prometheus (Metrics)  │  Grafana (UI)  │
    └──────────────────────────────────────────────────────────────┘
```

## Quick Start

```bash
# 1. Clone this boilerplate
git clone <repo-url> my-project && cd my-project

# 2. Copy environment config
cp .env.example .env

# 3. Start infrastructure
docker compose up -d postgres redis zookeeper kafka schema-registry kafka-ui jaeger prometheus grafana

# 4. Run in dev mode (live reload)
./mvnw quarkus:dev

# 5. Verify
curl http://localhost:8080/q/health
```

## Customize This Boilerplate

After cloning, follow these steps to make it yours:

### Step 1: Rename Identifiers

| What | Where | Change to |
|------|-------|-----------|
| Maven artifact | `pom.xml` → `<artifactId>` | `your-app-name` |
| Maven group | `pom.xml` → `<groupId>` | `com.yourcompany` |
| App name | `.env` → `APP_NAME` | `your-app` |
| DB name | `.env` → `DB_NAME` | `your_db` |
| JWT issuer | `.env` → `JWT_ISSUER` | `https://your-domain.com` |
| OTel service | `.env` → `OTEL_SERVICE_NAME` | `your-app` |

### Step 2: Rename Java Package

```bash
# Rename from com.example.app to com.yourcompany.yourapp
# 1. Move directories
mkdir -p src/main/java/com/yourcompany/yourapp
mv src/main/java/com/example/app/* src/main/java/com/yourcompany/yourapp/
rm -rf src/main/java/com/example

# 2. Find-and-replace in all Java files
find src -name "*.java" -exec sed -i 's/com\.example\.app/com.yourcompany.yourapp/g' {} +

# 3. Update proto files
find src -name "*.proto" -exec sed -i 's/com\.example\.app/com.yourcompany.yourapp/g' {} +

# 4. Update application.properties
sed -i 's/com\.example\.app/com.yourcompany.yourapp/g' src/main/resources/application.properties
```

### Step 3: Replace Sample Domain

The boilerplate ships with sample `User`, `Order`, `Payment` entities as reference implementations. For your project:

1. **Keep what fits** — if your app has users/orders, adapt the existing entities
2. **Add your domain** — follow the same pattern (entity → repo → service → resource → events → proto)
3. **Remove what you don't need** — delete unused entities, proto files, event types
4. **Update migrations** — edit `db/migration/V1__init_schema.sql` and `db/tenant-migrations/`

### Step 4: Choose Your Modules

Not every project needs all three modules. Disable what you don't need:

| Module | How to Disable |
|--------|---------------|
| Kafka | Remove `quarkus-messaging-kafka` from `pom.xml`, delete `event/` package, remove Kafka channels from `application.properties` |
| Multi-Tenancy | Delete `tenant/` package, remove `TenantFilter`, simplify to single-schema |
| gRPC | Remove `quarkus-grpc` from `pom.xml`, delete `grpc/` package and `proto/` files |

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Runtime | Java 21, Quarkus 3.17.x |
| API | JAX-RS (REST), gRPC (Protobuf) |
| Database | PostgreSQL 16, Hibernate ORM Panache |
| Migrations | Flyway |
| Messaging | Apache Kafka, SmallRye Reactive Messaging |
| Caching | Redis 7 |
| Security | SmallRye JWT, RBAC |
| Observability | OpenTelemetry, Jaeger, Prometheus, Grafana |
| Resilience | SmallRye Fault Tolerance |
| Build | Maven 3.9+, Docker |

## Prerequisites

- Java 21+ (GraalVM for native builds)
- Maven 3.9+ (or use `./mvnw`)
- Docker & Docker Compose v2

## Running Modes

```bash
# Development (live reload, drop-and-create DB)
./mvnw quarkus:dev

# Testing
./mvnw test

# Production JAR
./mvnw package -DskipTests
java -jar target/quarkus-app/quarkus-run.jar

# Docker
./mvnw package -DskipTests
docker compose up -d

# Native binary (requires GraalVM)
./mvnw package -Dnative -DskipTests
```

## Service Endpoints

| Service | URL | Notes |
|---------|-----|-------|
| REST API | http://localhost:8080 | Main application |
| OpenAPI / Swagger | http://localhost:8080/q/openapi | API documentation |
| Swagger UI | http://localhost:8080/q/swagger-ui | Interactive API explorer |
| gRPC Server | localhost:9000 | Protobuf over HTTP/2 |
| gRPC Gateway | http://localhost:8080/api/v1/grpc | REST-to-gRPC bridge |
| Health | http://localhost:8080/q/health | Liveness + Readiness |
| Metrics | http://localhost:8080/q/metrics | Prometheus format |
| Kafka UI | http://localhost:8090 | Topic browser |
| Jaeger UI | http://localhost:16686 | Distributed traces |
| Prometheus | http://localhost:9090 | Metrics queries |
| Grafana | http://localhost:3000 | Dashboards (admin/admin) |

## Module Details

### Kafka Event Handling

| Component | Description |
|-----------|-------------|
| `BaseEvent<T>` | Generic envelope with traceId, tenantId, versioning |
| `EventType` | Enum: USER_CREATED, ORDER_PLACED, PAYMENT_INITIATED, etc. |
| `KafkaEventProducer` | Sync/async/batch publishing with structured error handling |
| `*EventConsumer` | Per-topic consumers with manual offset commit, idempotency via Redis |
| `*EventHandler` | Business logic decoupled from transport |
| `DeadLetterHandler` | Persists failed events, admin retry endpoint |
| `OutboxPoller` | Polls every 5s, ensures DB+Kafka atomicity |

### Multi-Tenancy

| Component | Description |
|-----------|-------------|
| `TenantContext` | ThreadLocal tenant propagation |
| `TenantResolverChain` | Resolution: JWT → Header → Subdomain → Path → QueryParam |
| `TenantFilter` | Validates tenant on every request |
| `TenantAwareDataSourceProvider` | Dynamic schema routing with caching |
| `TenantProvisioningService` | Schema creation, Flyway migration, seeding |
| `QuotaEnforcementService` | Per-tenant user/storage quotas |
| `TenantAwareCache` | Redis with `{tenantId}:{key}` pattern |

### gRPC

| Service | RPCs |
|---------|------|
| `UserGrpcService` | CRUD + streaming + bidi chat |
| `OrderGrpcService` | CRUD + real-time streaming |
| `PaymentGrpcService` | Initiate, status, refund + streaming |

**Interceptors:** LoggingInterceptor → AuthInterceptor → TenantInterceptor

## How-To Guides

### Add a New Kafka Event

1. Add value to `EventType` enum
2. Create payload + event class in `event/domain/`
3. Add producer methods, consumer with `@Incoming`, handler
4. Add channel config in `application.properties` + `%test` override

### Add a New gRPC Method

1. Define message + RPC in `.proto` → `./mvnw compile`
2. Implement in `@GrpcService`, add mapper
3. (Optional) REST gateway endpoint

### Add a New Domain Entity

1. Entity in `domain/` → repository → service → resource
2. Flyway migration in `db/migration/` + `db/tenant-migrations/`
3. Events + gRPC if needed

## Project Structure

```
├── src/main/java/com/example/app/
│   ├── config/            # JacksonConfig, CorsConfig, OpenApiConfig, AppConfig
│   ├── domain/            # JPA entities
│   ├── repository/        # Panache repositories
│   ├── service/           # Business logic
│   ├── resource/          # REST endpoints
│   ├── exception/         # Custom exceptions + global mapper
│   ├── util/              # JsonUtil, EncryptionUtil, TraceContextPropagator
│   ├── event/             # Kafka: domain, producers, consumers, handlers, DLQ
│   ├── tenant/            # Multi-tenancy: context, resolvers, filters, datasource, provisioning
│   └── grpc/              # gRPC: services, interceptors, mappers, clients
├── src/main/proto/        # .proto files
├── src/main/resources/    # application.properties, Flyway migrations, JWT keys
├── src/test/              # Integration tests
├── infra/                 # Prometheus + Grafana configs
├── docs/                  # Architecture, Scaling, Deployment, Runbook
├── docker-compose.yml     # Full infrastructure stack
├── .env.example           # Environment variable reference
├── CONTRIBUTING.md        # Development guidelines
└── pom.xml
```

## Documentation

| Document | Description |
|----------|-------------|
| [Architecture](docs/ARCHITECTURE.md) | System design, module boundaries, ADRs, data flow |
| [Scaling Guide](docs/SCALING.md) | Horizontal/vertical scaling, K8s, DB sharding, Kafka partitioning |
| [Deployment Guide](docs/DEPLOYMENT.md) | CI/CD, Docker, Kubernetes, Helm, zero-downtime deploys |
| [Runbook](docs/RUNBOOK.md) | Incident response, troubleshooting, rollback procedures |
| [Contributing](CONTRIBUTING.md) | Dev setup, branching strategy, code standards, PR process |

## License

MIT
