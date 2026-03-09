# Architecture

## System Overview

This boilerplate is a modular monolith designed with microservice-ready boundaries. Each module (Kafka, Multi-Tenancy, gRPC) is isolated by package and can be extracted into a separate service when scale demands it.

**Design principles:**
- **Modular monolith** — single deployable with clear module boundaries
- **Event-driven** — modules communicate via Kafka events, not direct calls
- **Multi-tenant first** — every request is scoped to a tenant
- **API-agnostic services** — business logic is transport-independent (REST, gRPC, Kafka all delegate to the same service layer)

## Module Boundaries

```
┌──────────────────────────────────────────────────────────────────┐
│                         API LAYER                                │
│  REST Resources    gRPC Services    gRPC Gateway    Kafka Consumers
│  (resource/)       (grpc/service/)  (grpc/service/) (event/consumer/)
└──────────┬──────────────┬──────────────┬──────────────┬──────────┘
           │              │              │              │
           ▼              ▼              ▼              ▼
┌──────────────────────────────────────────────────────────────────┐
│                       SERVICE LAYER                              │
│  UserService    OrderService    PaymentService    TenantService  │
│                                                                  │
│  - Transaction boundaries                                        │
│  - Business rules & validation                                   │
│  - Event publishing (via producers)                              │
│  - Cross-service calls (via gRPC clients)                        │
└──────────┬──────────────┬──────────────┬──────────────┬──────────┘
           │              │              │              │
           ▼              ▼              ▼              ▼
┌──────────────────────────────────────────────────────────────────┐
│                    INFRASTRUCTURE LAYER                           │
│  Repositories     Event Producers   gRPC Clients    Tenant Cache │
│  (repository/)    (event/producer/) (grpc/client/)  (tenant/svc/)│
└──────────────────────────────────────────────────────────────────┘
```

**Key rule:** The API layer never directly accesses repositories or infrastructure. All business logic flows through the service layer.

## Data Flow Patterns

### 1. REST Request Flow

```
Client → TenantFilter → QuotaFilter → REST Resource → Service → Repository → PostgreSQL
                                                        ↓
                                                   EventProducer → Kafka
```

### 2. gRPC Request Flow

```
gRPC Client → LoggingInterceptor → AuthInterceptor → TenantInterceptor
  → gRPC Service Impl → Service → Repository → PostgreSQL
                                    ↓
                               EventProducer → Kafka
```

### 3. Kafka Event Flow

```
Kafka Topic → Consumer (idempotency check via Redis)
  → Set TenantContext from event.tenantId
  → EventHandler → Service (may call gRPC clients for enrichment)
  → Publish downstream events
```

### 4. Cross-Module Flow (Order → Payment)

```
OrderEventConsumer receives ORDER_PLACED
  → OrderEventHandler.onOrderPlaced()
    → UserGrpcClient.getUserById() (enrich user data)
    → PaymentGrpcClient.initiatePayment() (trigger payment via gRPC)
    → [fallback] publish PAYMENT_INITIATED event via Kafka
```

## Multi-Tenancy Architecture

### Schema-Per-Tenant (Primary)

Each tenant has an isolated PostgreSQL schema. Complete data isolation at the database level.

```
PostgreSQL
├── public schema        ← shared: tenants table, tenant_configs
├── tenant_acme schema   ← tenant data: users, orders, payments
├── tenant_globex schema ← tenant data: users, orders, payments
└── tenant_initech schema
```

**Pros:** Full isolation, per-tenant backup/restore, no risk of data leaks
**Cons:** Schema proliferation, migration complexity at scale (>1000 tenants)

### Row-Level Security (Fallback)

All tenants share a single schema. Data isolation via `tenant_id` column + filter.

**Pros:** Simple, scales to many tenants, single migration path
**Cons:** Shared indexes, no per-tenant backup, application-level enforcement

### When to Switch

| Tenants | Strategy | Reasoning |
|---------|----------|-----------|
| < 100 | Schema-Per-Tenant | Full isolation worth the overhead |
| 100-1000 | Hybrid | High-value tenants get schema, others row-level |
| > 1000 | Row-Level | Schema management becomes bottleneck |
| > 10000 | Row-Level + Sharding | Distribute across multiple DB instances |

## Event Architecture

### Event Envelope

Every event follows the `BaseEvent<T>` envelope pattern:

```json
{
  "eventId": "uuid",
  "eventType": "USER_CREATED",
  "occurredAt": "2024-01-01T00:00:00Z",
  "version": "1.0",
  "traceId": "w3c-trace-id",
  "tenantId": "acme",
  "payload": { ... }
}
```

### Topic Strategy

Single topic per aggregate type. Tenant isolation via event key (tenantId).

| Topic | Key | Consumers |
|-------|-----|-----------|
| `user-events` | tenantId | UserEventConsumer |
| `order-events` | tenantId | OrderEventConsumer |
| `payment-events` | tenantId | PaymentEventConsumer |
| `notification-events` | tenantId | (future) NotificationConsumer |
| `dead-letter-queue` | original-topic | DeadLetterHandler |

### Idempotency

Consumers store processed `eventId` in Redis with a TTL (24h default). Duplicate events are skipped. This is critical for at-least-once delivery guarantees.

### Outbox Pattern

For operations requiring atomicity between DB writes and event publishing:

1. Service writes entity + `OutboxEvent` in same transaction
2. `OutboxPoller` (every 5s) reads pending outbox events
3. Publishes to Kafka, marks as processed
4. Guarantees no event loss even if Kafka is temporarily unavailable

## Architecture Decision Records (ADR)

### ADR-001: Modular Monolith over Microservices

**Context:** Starting a new product with a small team (2-5 devs).
**Decision:** Build as a modular monolith with clean module boundaries.
**Rationale:** Faster iteration, simpler ops, can extract services later.
**Consequences:** Must maintain strict module boundaries to enable future extraction.

### ADR-002: Schema-Per-Tenant as Primary Strategy

**Context:** Enterprise customers require strong data isolation guarantees.
**Decision:** Use PostgreSQL schema-per-tenant with row-level as fallback.
**Rationale:** Schema isolation meets compliance requirements (SOC2, GDPR).
**Consequences:** Need automated schema lifecycle management, migration complexity.

### ADR-003: Kafka over RabbitMQ/NATS

**Context:** Need async messaging with ordering guarantees and replay capability.
**Decision:** Apache Kafka with SmallRye Reactive Messaging.
**Rationale:** Kafka provides durable, ordered, replayable event log. Scales to millions of events/sec. Confluent ecosystem (Schema Registry, Connect).
**Consequences:** Operational complexity (Zookeeper/KRaft), requires more memory.

### ADR-004: gRPC for Inter-Service Communication

**Context:** Need efficient binary communication for internal service calls.
**Decision:** gRPC with Protocol Buffers.
**Rationale:** Type-safe contracts, streaming support, 10x faster serialization vs JSON, code generation for clients.
**Consequences:** Requires proto file management, learning curve for team, REST gateway for external consumers.

### ADR-005: Transactional Outbox over CDC

**Context:** Need to ensure consistency between DB writes and Kafka publishes.
**Decision:** Polling-based outbox pattern.
**Rationale:** Simpler than CDC (Debezium), no additional infrastructure, good enough for current throughput (<10K events/min).
**Consequences:** Up to 5s delay between write and publish. Switch to CDC if latency or throughput becomes an issue.

### ADR-006: OpenTelemetry for Observability

**Context:** Need distributed tracing across REST, Kafka, and gRPC.
**Decision:** OpenTelemetry with Jaeger backend.
**Rationale:** Vendor-neutral standard, Quarkus native support, can switch backends (Zipkin, Datadog, Tempo) without code changes.
**Consequences:** Small overhead per request (~1-2ms). Sampling strategy needed for production.

## Extraction Playbook

When a module needs to become its own service:

1. **Identify the boundary**: module should only depend on its own domain + shared kernel
2. **Replace direct DB access**: use events or gRPC instead of shared DB queries
3. **Extract proto contracts**: the module's `.proto` file becomes the service API
4. **Create separate repo**: copy module packages, add own `pom.xml`
5. **Deploy independently**: own container, own DB, own Kafka consumer group
6. **Update gRPC clients**: point existing services to the new service host
7. **Remove from monolith**: delete extracted packages, keep gRPC client

**Extraction order (recommended):**
1. Payment Service (least dependencies, clear boundary)
2. Notification Service (event-only interface)
3. User Service (most depended on, extract last)
