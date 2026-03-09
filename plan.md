You are a senior Java backend engineer specializing in enterprise microservices.
Extend the existing Quarkus 3.x boilerplate with three advanced modules:
Kafka Event Handling, Multi-Tenancy, and gRPC. Generate all files completely
with no placeholders. Include all imports.

Base package: com.example.app
Java 21, Quarkus 3.x, Maven

════════════════════════════════════════════════════════════
MODULE 1: KAFKA EVENT HANDLING
════════════════════════════════════════════════════════════

## Additional Extensions

- quarkus-messaging-kafka
- quarkus-avro (schema registry)
- quarkus-smallrye-reactive-messaging

## Folder Structure

src/main/java/com/example/app/
├── event/
│ ├── domain/ # Event payload POJOs
│ ├── producer/ # Kafka producers
│ ├── consumer/ # Kafka consumers
│ ├── handler/ # Business logic triggered by events
│ └── deadletter/ # Dead letter queue handlers

## Code to Generate

### 1. Base Event Envelope

Create abstract class BaseEvent<T>:

- eventId (UUID, auto-generated)
- eventType (String)
- occurredAt (Instant)
- version (String, default "1.0")
- traceId (String, for distributed tracing)
- tenantId (String, for multi-tenancy)
- payload (T)
- static factory method: BaseEvent.of(eventType, payload, tenantId)

### 2. Event Types Enum

EventType enum covering lifecycle events:
USER_CREATED, USER_UPDATED, USER_DELETED,
ORDER_PLACED, ORDER_CANCELLED, ORDER_COMPLETED,
PAYMENT_INITIATED, PAYMENT_SUCCESS, PAYMENT_FAILED,
NOTIFICATION_REQUESTED

### 3. Domain Events (one per aggregate)

UserEvent extends BaseEvent<UserEventPayload>:

- UserEventPayload: userId, email, fullName, role, action

OrderEvent extends BaseEvent<OrderEventPayload>:

- OrderEventPayload: orderId, userId, totalAmount, status, items (List)

PaymentEvent extends BaseEvent<PaymentEventPayload>:

- PaymentEventPayload: paymentId, orderId, amount, currency, status, gatewayRef

### 4. Kafka Producer

@ApplicationScoped KafkaEventProducer:

- Inject @Channel for each topic
- publish(BaseEvent<?> event): serialize to JSON, send with key=tenantId
- publishAsync(BaseEvent<?> event): returns Uni<Void>
- publishBatch(List<BaseEvent<?>> events): send all in sequence
- Handle serialization errors gracefully with structured logging

### 5. Topic-Specific Producers

UserEventProducer:

- publishUserCreated(UserEntity user, String tenantId)
- publishUserUpdated(UserEntity user, String tenantId)
- publishUserDeleted(UUID userId, String tenantId)

OrderEventProducer:

- publishOrderPlaced(Order order, String tenantId)
- publishOrderStatusChanged(Order order, String previousStatus, String tenantId)

### 6. Kafka Consumers

@ApplicationScoped UserEventConsumer:

- @Incoming("user-events-in") on processUserEvent(BaseEvent<UserEventPayload> event)
- Filter by tenantId from event envelope
- Delegate to UserEventHandler for business logic
- Commit offset manually after successful processing

@ApplicationScoped OrderEventConsumer:

- @Incoming("order-events-in") on processOrderEvent(...)
- Idempotency check: store eventId in Redis, skip if already processed
- Delegate to OrderEventHandler

### 7. Event Handlers

UserEventHandler:

- onUserCreated: send welcome notification, init user preferences
- onUserUpdated: invalidate cache, update search index
- onUserDeleted: cleanup related resources

OrderEventHandler:

- onOrderPlaced: reserve inventory, trigger payment
- onOrderCancelled: release inventory, initiate refund
- onOrderCompleted: update loyalty points, send receipt

### 8. Dead Letter Queue Handler

@ApplicationScoped DeadLetterHandler:

- @Incoming("dead-letter-queue") consume failed events
- Log full event with error context
- Persist to dead_letter_events table (eventId, topic, payload, error, retryCount, createdAt)
- Expose POST /api/v1/admin/dlq/{eventId}/retry endpoint to replay event

### 9. Outbox Pattern

OutboxEvent entity (id, aggregateId, aggregateType, eventType, payload, status, createdAt, processedAt)
OutboxEventRepository (findPending, markProcessed)
OutboxPoller: @Scheduled(every="5s") poll pending outbox events and publish to Kafka

### 10. application.properties for Kafka

mp.messaging.outgoing.user-events-out.connector=smallrye-kafka
mp.messaging.outgoing.user-events-out.topic=user-events
mp.messaging.outgoing.user-events-out.value.serializer=org.apache.kafka.common.serialization.StringSerializer
mp.messaging.outgoing.user-events-out.key.serializer=org.apache.kafka.common.serialization.StringSerializer
mp.messaging.outgoing.user-events-out.acks=all
mp.messaging.outgoing.user-events-out.retries=3

mp.messaging.incoming.user-events-in.connector=smallrye-kafka
mp.messaging.incoming.user-events-in.topic=user-events
mp.messaging.incoming.user-events-in.group.id=user-service-group
mp.messaging.incoming.user-events-in.auto.offset.reset=earliest
mp.messaging.incoming.user-events-in.enable.auto.commit=false

kafka.bootstrap.servers=localhost:9092

# Repeat pattern for order-events, payment-events, dead-letter-queue

### 11. Docker services for Kafka

Add to docker-compose.yml:

- zookeeper (confluentinc/cp-zookeeper:7.5.0)
- kafka broker (confluentinc/cp-kafka:7.5.0) with PLAINTEXT listener
- kafka-ui (provectuslabs/kafka-ui) on port 8090
- schema-registry (confluentinc/cp-schema-registry:7.5.0)

### 12. Kafka Integration Test

@QuarkusTest KafkaIntegrationTest:

- Use @QuarkusTestResource(KafkaCompanionResource.class)
- Test UserEventProducer publishes correct event on user creation
- Test UserEventConsumer processes event and triggers UserEventHandler
- Assert idempotency: duplicate eventId is skipped

════════════════════════════════════════════════════════════
MODULE 2: MULTI-TENANCY
════════════════════════════════════════════════════════════

## Additional Extensions

- quarkus-hibernate-orm-panache (already included)
- quarkus-liquibase-mongodb OR quarkus-flyway (already included)

## Strategy: Schema-Per-Tenant (Primary) + Row-Level (Fallback)

Support both strategies, switchable via config:
app.multitenancy.strategy=SCHEMA # or ROW_LEVEL

## Folder Structure

src/main/java/com/example/app/
├── tenant/
│ ├── context/ # TenantContext (thread-local)
│ ├── resolver/ # Tenant resolution strategies
│ ├── filter/ # JAX-RS request filter
│ ├── datasource/ # Dynamic datasource routing
│ ├── domain/ # TenantEntity, TenantConfig
│ ├── service/ # TenantService, TenantProvisioningService
│ └── resource/ # Admin endpoints for tenant management

## Code to Generate

### 1. TenantContext

@RequestScoped TenantContext:

- ThreadLocal<String> tenantId
- static getCurrentTenant(): String
- static setCurrentTenant(String tenantId)
- static clear()
- isValid(): boolean (check non-null, non-empty, alphanumeric only)
- String DEFAULT_TENANT = "public"

### 2. Tenant Resolution Strategies

Interface TenantResolver with method: String resolve(ContainerRequestContext ctx)

Implementations:

- HeaderTenantResolver: extract from X-Tenant-ID header
- SubdomainTenantResolver: extract from Host header (e.g., acme.app.com → "acme")
- JwtClaimTenantResolver: extract from JWT claim "tenant_id"
- PathTenantResolver: extract from URL path /api/v1/{tenantId}/resource
- QueryParamTenantResolver: extract from ?tenant= query param (dev/testing only)

@ApplicationScoped TenantResolverChain:

- Try resolvers in order: JWT → Header → Subdomain → Path
- Throw UnauthorizedException if no tenant resolved

### 3. Tenant Request Filter

@Provider @Priority(Priorities.AUTHENTICATION - 10) TenantFilter implements ContainerRequestFilter:

- Resolve tenant via TenantResolverChain
- Validate tenant exists and is ACTIVE in DB
- Set TenantContext.setCurrentTenant(tenantId)
- Add tenantId to MDC for logging
- @Context inject UriInfo to skip /api/v1/public/** and /q/** paths

### 4. Tenant Entity & Repository

TenantEntity (id UUID, tenantId String unique, name, status ENUM(ACTIVE/SUSPENDED/PROVISIONING),
plan ENUM(FREE/STARTER/PROFESSIONAL/ENTERPRISE), schemaName, dbUrl, dbUsername, dbPassword encrypted,
maxUsers int, storageQuotaGb int, features JSONB, createdAt, updatedAt)

TenantConfig entity (tenantId, configKey, configValue, isEncrypted, updatedAt)
TenantRepository extends PanacheRepository<TenantEntity>
TenantConfigRepository

### 5. Dynamic Datasource Routing (Schema-Per-Tenant)

@ApplicationScoped TenantAwareDataSourceProvider:

- Cache Map<tenantId, DataSource>
- getDataSource(String tenantId): DataSource
  - If cached, return cached
  - Else build new DataSource from TenantEntity credentials
  - Set schema search_path for PostgreSQL: SET search_path TO {tenantId}
- evictTenant(String tenantId): remove from cache

@ApplicationScoped TenantAwareEntityManagerProducer:

- @Produces @RequestScoped EntityManager
- Route to correct schema via TenantContext
- @Disposes to close after request

### 6. Row-Level Security Strategy (Fallback)

@MappedSuperclass TenantAwareEntity extends BaseEntity:

- String tenantId (non-null)
- @PrePersist: auto-set tenantId from TenantContext

TenantAwareRepository<T extends TenantAwareEntity> extends PanacheRepository<T>:

- Override findAll() → add WHERE tenant_id = TenantContext.getCurrentTenant()
- Override findById(UUID id) → add AND tenant_id = ... check
- Override count() → scoped to tenant
- listByTenant(String tenantId, Page page)

### 7. Tenant Provisioning Service

@ApplicationScoped TenantProvisioningService:

- provisionTenant(CreateTenantRequest req): Uni<TenantEntity>
  1. Validate tenantId format (lowercase alphanumeric, 3-30 chars)
  2. Check tenantId not already taken
  3. Create TenantEntity with status=PROVISIONING
  4. (Schema strategy) Create PostgreSQL schema, run Flyway migrations scoped to schema
  5. Seed default data (admin user, default config)
  6. Update status=ACTIVE
  7. Publish TenantProvisionedEvent to Kafka
  8. Return TenantEntity
- suspendTenant(String tenantId)
- deleteTenant(String tenantId): soft delete, schedule hard delete after 30 days

### 8. Tenant Service & Resource

TenantService: CRUD + usage stats (userCount, storageUsed)
TenantResource @Path("/api/v1/admin/tenants") @RolesAllowed("SUPER_ADMIN"):

- POST / → createTenant (triggers provisioning)
- GET / → listTenants (paginated)
- GET /{tenantId} → getTenant
- PUT /{tenantId} → updateTenant (plan, features, quotas)
- POST /{tenantId}/suspend
- POST /{tenantId}/activate
- DELETE /{tenantId}
- GET /{tenantId}/stats → usage statistics

### 9. Tenant-Scoped Caching

TenantAwareCache:

- Use Redis with key pattern: "{tenantId}:{cacheKey}"
- get(String key): String → prefixes with tenantId automatically
- put(String key, String value, Duration ttl)
- evict(String key)
- evictAll(): clear all keys for current tenant

### 10. Tenant Quota Enforcement

@ApplicationScoped QuotaEnforcementService:

- checkUserQuota(String tenantId): throw QuotaExceededException if maxUsers reached
- checkStorageQuota(String tenantId, long bytesToAdd)
  @Provider QuotaEnforcementFilter: intercept POST /api/v1/users and check quota before proceeding

### 11. Tenant Flyway Migration (Schema Strategy)

TenantFlywayMigrationRunner:

- On provisionTenant: run Flyway with schema={tenantId}
- Flyway.configure().schemas(tenantId).locations("classpath:db/tenant-migrations").load().migrate()
- Separate migration folder: src/main/resources/db/tenant-migrations/
  - V1\_\_init_tenant_schema.sql (users, orders, etc. without tenant_id column)

### 12. application.properties for Multi-Tenancy

app.multitenancy.strategy=SCHEMA
app.multitenancy.tenant-header=X-Tenant-ID
app.multitenancy.default-schema=public
app.multitenancy.cache.datasource-ttl=PT30M
app.multitenancy.public-paths=/api/v1/auth,/api/v1/public,/q/health

### 13. Multi-Tenancy Tests

@QuarkusTest TenantFilterTest: verify correct tenant resolved from header/JWT
@QuarkusTest TenantProvisioningTest: mock DB, verify schema creation and seed data
@QuarkusTest RowLevelSecurityTest: verify tenant data isolation — tenant A cannot read tenant B data

════════════════════════════════════════════════════════════
MODULE 3: gRPC
════════════════════════════════════════════════════════════

## Additional Extensions

- quarkus-grpc
- quarkus-grpc-stubs (for testing)

## Folder Structure

src/
├── main/
│ ├── java/com/example/app/
│ │ └── grpc/
│ │ ├── service/ # gRPC service implementations
│ │ ├── interceptor/ # Server interceptors (auth, tenant, logging)
│ │ ├── mapper/ # Proto ↔ Domain mappers
│ │ └── client/ # gRPC client stubs for inter-service calls
│ └── proto/
│ ├── common.proto # Shared messages
│ ├── user.proto
│ ├── order.proto
│ └── payment.proto

## Code to Generate

### 1. common.proto

syntax = "proto3";
package com.example.app.grpc;
option java_package = "com.example.app.grpc.proto";
option java_multiple_files = true;

Messages:

- Timestamp { int64 seconds = 1; int32 nanos = 2; }
- PageRequest { int32 page = 1; int32 size = 2; string sort_by = 3; string sort_dir = 4; }
- PageInfo { int64 total_elements = 1; int32 total_pages = 2; int32 current_page = 3; }
- ErrorDetail { string code = 1; string message = 2; string field = 3; }
- Empty {}

### 2. user.proto

Messages:

- UserProto { string id, email, full_name, role, status, Timestamp created_at, Timestamp updated_at }
- CreateUserRequest { string email, password, full_name, role }
- UpdateUserRequest { string id, string full_name, string role, bool is_active }
- GetUserRequest { string id }
- DeleteUserRequest { string id }
- ListUsersRequest { PageRequest page, string role_filter, string status_filter, string tenant_id }
- ListUsersResponse { repeated UserProto users, PageInfo page_info }
- UserExistsRequest { string email }
- UserExistsResponse { bool exists, string user_id }

Service UserGrpcService:

- rpc GetUser(GetUserRequest) returns (UserProto)
- rpc ListUsers(ListUsersRequest) returns (ListUsersResponse)
- rpc CreateUser(CreateUserRequest) returns (UserProto)
- rpc UpdateUser(UpdateUserRequest) returns (UserProto)
- rpc DeleteUser(DeleteUserRequest) returns (Empty)
- rpc UserExists(UserExistsRequest) returns (UserExistsResponse)
- rpc StreamUsers(ListUsersRequest) returns (stream UserProto) ← server streaming
- rpc BatchCreateUsers(stream CreateUserRequest) returns (ListUsersResponse) ← client streaming

### 3. order.proto

Messages:

- OrderItemProto { string product_id, string name, int32 quantity, double unit_price }
- OrderProto { string id, string user_id, repeated OrderItemProto items, double total_amount,
  string status, string tenant_id, Timestamp created_at }
- CreateOrderRequest { string user_id, repeated OrderItemProto items, string tenant_id }
- GetOrderRequest { string id }
- ListOrdersByUserRequest { string user_id, PageRequest page }
- ListOrdersResponse { repeated OrderProto orders, PageInfo page_info }
- UpdateOrderStatusRequest { string id, string status, string reason }

Service OrderGrpcService:

- rpc CreateOrder(CreateOrderRequest) returns (OrderProto)
- rpc GetOrder(GetOrderRequest) returns (OrderProto)
- rpc ListOrdersByUser(ListOrdersByUserRequest) returns (ListOrdersResponse)
- rpc UpdateOrderStatus(UpdateOrderStatusRequest) returns (OrderProto)
- rpc StreamOrderUpdates(GetOrderRequest) returns (stream OrderProto) ← real-time updates

### 4. payment.proto

Messages:

- PaymentProto { string id, string order_id, double amount, string currency, string status, string gateway_ref }
- InitiatePaymentRequest { string order_id, double amount, string currency, string method }
- PaymentStatusRequest { string payment_id }
- RefundRequest { string payment_id, double amount, string reason }
- RefundResponse { string refund_id, string status, Timestamp processed_at }

Service PaymentGrpcService:

- rpc InitiatePayment(InitiatePaymentRequest) returns (PaymentProto)
- rpc GetPaymentStatus(PaymentStatusRequest) returns (PaymentProto)
- rpc ProcessRefund(RefundRequest) returns (RefundResponse)
- rpc StreamPaymentEvents(PaymentStatusRequest) returns (stream PaymentProto)

### 5. gRPC Service Implementations

@GrpcService UserGrpcServiceImpl extends UserGrpcServiceImplBase:

- Inject UserService
- Inject UserGrpcMapper
- Override all RPC methods
- Map domain exceptions to gRPC StatusRuntimeException:
  - NotFoundException → Status.NOT_FOUND
  - ValidationException → Status.INVALID_ARGUMENT
  - UnauthorizedException → Status.UNAUTHENTICATED
  - ConflictException → Status.ALREADY_EXISTS
  - Generic → Status.INTERNAL
- For server streaming StreamUsers: fetch paginated users and emit each via responseObserver.onNext()

@GrpcService OrderGrpcServiceImpl: same pattern
@GrpcService PaymentGrpcServiceImpl: same pattern

### 6. gRPC Server Interceptors

AuthInterceptor implements ServerInterceptor:

- Extract Bearer token from Metadata key "authorization"
- Validate JWT via JwtTokenUtil
- Attach SecurityIdentity to Context
- Reject unauthenticated calls with Status.UNAUTHENTICATED
- Skip auth for: UserGrpcService/UserExists, health check

TenantInterceptor implements ServerInterceptor:

- Extract tenantId from Metadata key "x-tenant-id"
- Set TenantContext.setCurrentTenant(tenantId)
- Clear TenantContext in finally block
- Throw Status.INVALID_ARGUMENT if tenant missing/invalid

LoggingInterceptor implements ServerInterceptor:

- Log method name, tenantId, userId, duration
- Use MDC for structured logging
- Log request size and response size

@ApplicationScoped GrpcInterceptorRegistrar:

- Register interceptors in order: Logging → Auth → Tenant

### 7. gRPC ↔ Domain Mappers

@ApplicationScoped UserGrpcMapper:

- toProto(UserEntity entity): UserProto
- toEntity(CreateUserRequest req): UserEntity
- toProto(Timestamp instant): com.google.protobuf.Timestamp
- toInstant(com.google.protobuf.Timestamp ts): Instant
- toProto(Page<UserEntity> page): ListUsersResponse

@ApplicationScoped OrderGrpcMapper: same pattern
@ApplicationScoped PaymentGrpcMapper: same pattern

### 8. gRPC Clients (Inter-Service Communication)

@ApplicationScoped UserGrpcClient:

- @GrpcClient("user-service") inject UserGrpcServiceGrpc.UserGrpcServiceBlockingStub
- @GrpcClient("user-service") inject UserGrpcServiceGrpc.UserGrpcServiceStub (async)
- checkUserExists(String email): boolean
- getUserById(String userId): Optional<UserProto>
- Use ManagedChannelBuilder with keepAlive, deadlines, retry policy

@ApplicationScoped PaymentGrpcClient (called by Order service):

- initiatePayment(String orderId, double amount, String currency): PaymentProto
- getPaymentStatus(String paymentId): PaymentProto

### 9. Bidirectional Streaming Example

In user.proto add:

- rpc Chat(stream ChatMessage) returns (stream ChatMessage) ← bidi streaming example

ChatMessage { string sender_id, string content, Timestamp sent_at, string tenant_id }

ChatGrpcServiceImpl: maintain Map<tenantId, List<StreamObserver>> for room-per-tenant chat

### 10. gRPC Health Check

Implement grpc.health.v1.Health service:

- check(HealthCheckRequest): return SERVING / NOT_SERVING
- watch(HealthCheckRequest): stream status changes
  Register with quarkus.grpc.server.enable-reflection=true

### 11. application.properties for gRPC

quarkus.grpc.server.port=9000
quarkus.grpc.server.ssl.certificate=certs/server.crt
quarkus.grpc.server.ssl.key=certs/server.key
quarkus.grpc.server.enable-reflection=true
quarkus.grpc.server.max-inbound-message-size=4194304

# Client config

quarkus.grpc.clients.user-service.host=user-service
quarkus.grpc.clients.user-service.port=9000
quarkus.grpc.clients.user-service.ssl.trust-certificate-pem=certs/ca.crt
quarkus.grpc.clients.payment-service.host=payment-service
quarkus.grpc.clients.payment-service.port=9000

### 12. gRPC Gateway (REST → gRPC Bridge)

@Path("/api/v1/grpc") GrpcGatewayResource:

- Acts as REST facade over gRPC services
- POST /users → calls UserGrpcClient.createUser() via gRPC
- GET /users/{id} → calls UserGrpcClient.getUserById()
- Use for backward compatibility with REST clients
- Add @Timeout(5000) and @Retry(maxRetries=2) via SmallRye Fault Tolerance

### 13. gRPC Tests

@QuarkusTest UserGrpcServiceTest:

- Use @GrpcClient to call service in test
- Test GetUser: verify mapping, status codes
- Test StreamUsers: collect all streamed items, verify count
- Test AuthInterceptor: call without token → expect UNAUTHENTICATED
- Test TenantInterceptor: call without tenant → expect INVALID_ARGUMENT

@QuarkusTest GrpcClientTest:

- Use @QuarkusTestResource(GrpcServerTestResource.class)
- Mock gRPC server with InProcessServerBuilder
- Test UserGrpcClient.checkUserExists()

════════════════════════════════════════════════════════════
CROSS-CUTTING: INTEGRATE ALL 3 MODULES
════════════════════════════════════════════════════════════

### 1. Tenant-Aware Kafka Events

- All BaseEvent payloads carry tenantId
- KafkaConsumers extract tenantId and call TenantContext.setCurrentTenant() before delegating
- Topic naming convention: {tenantId}.{eventType} OR use single topic with tenantId in key
- Kafka ACL per tenant (document config, do not generate)

### 2. gRPC Tenant Propagation

- TenantInterceptor propagates tenantId from gRPC Metadata → TenantContext
- When UserGrpcClient calls another service, attach current tenantId to outgoing Metadata:
  ClientInterceptor TenantClientInterceptor: add "x-tenant-id" to every outgoing call

### 3. Kafka → gRPC Flow Example

When OrderEventConsumer receives ORDER_PLACED:

1. Extract tenantId from event
2. Set TenantContext
3. Call UserGrpcClient.getUserById(order.userId) to enrich event data
4. Call PaymentGrpcClient.initiatePayment(...)
5. Publish PAYMENT_INITIATED event back to Kafka

### 4. Distributed Tracing (OpenTelemetry)

Add quarkus-opentelemetry extension.
Propagate traceId across:

- REST requests (via W3C Trace-Context header)
- Kafka messages (via BaseEvent.traceId field)
- gRPC calls (via Metadata "traceparent" key)
  @ApplicationScoped TraceContextPropagator: utility to extract/inject traceId

### 5. Full docker-compose.yml

Services:

- app (Quarkus, port 8080 REST + 9000 gRPC)
- postgres (port 5432)
- redis (port 6379)
- zookeeper (port 2181)
- kafka (port 9092)
- kafka-ui (port 8090, provectuslabs/kafka-ui)
- schema-registry (port 8081)
- jaeger (port 16686 UI, 4317 OTLP, for distributed tracing)
- prometheus (port 9090)
- grafana (port 3000, pre-loaded Quarkus dashboard)
  All services on network: app-network

### 6. Full .env.example

DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD
REDIS_HOST, REDIS_PORT
KAFKA_BOOTSTRAP_SERVERS
SCHEMA_REGISTRY_URL
JWT_SECRET, JWT_EXPIRY_SECONDS
GRPC_PORT
OTEL_EXPORTER_OTLP_ENDPOINT
ENCRYPTION_KEY (for TenantEntity.dbPassword encryption)

### 7. README.md

Include:

- Architecture diagram (ASCII art showing REST/gRPC → Services → Kafka → DB/Redis)
- Prerequisites and setup steps
- How to add a new tenant
- How to add a new Kafka event
- How to add a new gRPC method
- Environment variable reference table

Generate every file in full. No TODOs, no placeholders, no "// implement this".
Every method must have a real implementation.
