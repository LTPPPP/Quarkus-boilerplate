# Scaling Guide

## Scaling Strategy Overview

```
                    SCALING DIMENSIONS
     ┌──────────────────────────────────────────┐
     │                                          │
     │   Vertical          Horizontal           │
     │   (bigger)          (more)               │
     │                                          │
     │   ┌──────┐      ┌──┐ ┌──┐ ┌──┐         │
     │   │      │      │  │ │  │ │  │         │
     │   │      │      │  │ │  │ │  │         │
     │   │  ▲   │      │  │ │  │ │  │         │
     │   │  │   │      └──┘ └──┘ └──┘         │
     │   │  │   │                               │
     │   └──────┘      ← Preferred approach     │
     │                                          │
     └──────────────────────────────────────────┘
```

## Phase 1: Single Instance Optimization (0 → 1K users)

Focus: squeeze maximum performance from one instance.

### Application Tuning

```properties
# Connection pool (match to CPU cores * 2)
quarkus.datasource.jdbc.max-size=30
quarkus.datasource.jdbc.min-size=10

# HTTP thread pool
quarkus.thread-pool.max-threads=200
quarkus.thread-pool.core-threads=50

# gRPC server
quarkus.grpc.server.max-inbound-message-size=4194304
quarkus.grpc.server.handshake-timeout=10S

# Kafka consumer tuning
mp.messaging.incoming.*.max.poll.records=500
mp.messaging.incoming.*.fetch.min.bytes=1024
mp.messaging.incoming.*.fetch.max.wait.ms=500
```

### Database Optimization

```sql
-- Essential indexes for multi-tenant queries
CREATE INDEX idx_users_tenant_email ON users(tenant_id, email);
CREATE INDEX idx_users_tenant_status ON users(tenant_id, status);
CREATE INDEX idx_orders_user_status ON orders(user_id, status);
CREATE INDEX idx_orders_tenant_created ON orders(tenant_id, created_at DESC);
CREATE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_outbox_status_created ON outbox_events(status, created_at) WHERE status = 'PENDING';

-- PostgreSQL tuning (postgresql.conf)
-- shared_buffers = 25% of RAM
-- effective_cache_size = 75% of RAM
-- work_mem = 64MB
-- maintenance_work_mem = 512MB
-- max_connections = 200
```

### Redis Optimization

```properties
# Connection pool
quarkus.redis.max-pool-size=32
quarkus.redis.max-waiting-handlers=64

# Key expiry strategy
# Use TTL on all cached keys (default 30min for tenant cache)
# Enable Redis memory policy: allkeys-lru
```

### Native Build

For 10x faster startup and 5x lower memory:

```bash
./mvnw package -Dnative -DskipTests
# Result: ~50MB binary, starts in <100ms, uses ~100MB RAM
```

## Phase 2: Horizontal Scaling (1K → 100K users)

### Application Layer

Deploy multiple instances behind a load balancer.

```yaml
# docker-compose.override.yml
services:
  app:
    deploy:
      replicas: 3
    environment:
      QUARKUS_HTTP_HOST: 0.0.0.0
```

**Requirements for horizontal scaling:**
- Application is **stateless** (TenantContext is request-scoped, not sticky)
- Sessions/state stored in Redis (not in-memory)
- Kafka consumer groups automatically rebalance across instances
- gRPC load balancing via client-side (round-robin) or service mesh

### Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: myapp
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  template:
    spec:
      containers:
      - name: myapp
        image: myapp:latest
        ports:
        - containerPort: 8080
          name: http
        - containerPort: 9000
          name: grpc
        resources:
          requests:
            cpu: 500m
            memory: 512Mi
          limits:
            cpu: 2000m
            memory: 2Gi
        readinessProbe:
          httpGet:
            path: /q/health/ready
            port: 8080
          initialDelaySeconds: 5
          periodSeconds: 10
        livenessProbe:
          httpGet:
            path: /q/health/live
            port: 8080
          initialDelaySeconds: 15
          periodSeconds: 20
        startupProbe:
          httpGet:
            path: /q/health/started
            port: 8080
          failureThreshold: 30
          periodSeconds: 2
```

### Horizontal Pod Autoscaler

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: myapp-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: myapp
  minReplicas: 3
  maxReplicas: 20
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 60
      policies:
      - type: Pods
        value: 2
        periodSeconds: 60
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
      - type: Pods
        value: 1
        periodSeconds: 120
```

### Kafka Scaling

**Partition Strategy:**

| Topic | Initial Partitions | Scaling Rule |
|-------|-------------------|--------------|
| `user-events` | 6 | 2x app instances |
| `order-events` | 12 | High volume, 2x app instances |
| `payment-events` | 6 | 2x app instances |
| `dead-letter-queue` | 3 | Low volume |

```bash
# Increase partitions (never decrease)
kafka-topics --bootstrap-server kafka:9092 \
  --alter --topic order-events --partitions 24
```

**Consumer group scaling:** Kafka automatically distributes partitions across consumer instances. Max parallelism = number of partitions.

**Key design:** Events are keyed by `tenantId` ensuring all events for a tenant go to the same partition (ordering guarantee per tenant).

### Database Scaling

**Step 1: Read Replicas**

```properties
# Primary (writes)
quarkus.datasource.jdbc.url=jdbc:postgresql://primary:5432/appdb

# Read replica (queries)
quarkus.datasource."read-replica".db-kind=postgresql
quarkus.datasource."read-replica".jdbc.url=jdbc:postgresql://replica:5432/appdb
quarkus.datasource."read-replica".jdbc.max-size=30
```

**Step 2: Connection Pooling (PgBouncer)**

```
App (N instances) → PgBouncer (connection multiplexer) → PostgreSQL
     200 conns            → 50 conns to DB
```

**Step 3: Tenant-Based Sharding**

```
Tenants A-M  → PostgreSQL Shard 1
Tenants N-Z  → PostgreSQL Shard 2
```

Implement in `TenantAwareDataSourceProvider` by routing based on tenant ID hash.

### Redis Scaling

**Step 1:** Redis Sentinel for HA (automatic failover)
**Step 2:** Redis Cluster for sharding (data distributed across nodes)
**Step 3:** Separate Redis instances per concern (cache vs. idempotency vs. rate limiting)

## Phase 3: Service Extraction (100K+ users)

When the monolith hits scaling limits, extract high-traffic modules.

### Extraction Priority

```
1. Payment Service    ← Separate scaling requirements, compliance isolation
2. Notification Svc   ← Pure event consumer, no shared state
3. Order Service      ← High write volume, benefit from dedicated DB
4. User Service       ← Core dependency, extract last
```

### Per-Service Scaling

| Service | Scaling Characteristic | Strategy |
|---------|----------------------|----------|
| User Service | Read-heavy | Cache aggressively, read replicas |
| Order Service | Write-heavy | DB sharding by user_id, more partitions |
| Payment Service | Latency-sensitive | Dedicated resources, circuit breakers |
| Notification Svc | Burst-heavy | Queue-based, scale consumers independently |

## Monitoring Scaling Health

### Key Metrics to Watch

| Metric | Warning | Critical | Action |
|--------|---------|----------|--------|
| HTTP p95 latency | > 500ms | > 2s | Scale out, check DB |
| Kafka consumer lag | > 10K | > 100K | Add consumers, check handler perf |
| DB connection pool wait | > 100ms | > 1s | Increase pool or add PgBouncer |
| JVM heap usage | > 70% | > 90% | Increase memory limit |
| CPU usage | > 70% | > 90% | Scale out (HPA should handle) |
| Error rate (5xx) | > 1% | > 5% | Investigate, potentially rollback |

### Grafana Alerts

Set up alerts in Grafana for the metrics above. The pre-configured dashboard (`infra/grafana/provisioning/dashboards/quarkus-dashboard.json`) includes panels for all these metrics.

## Capacity Planning

### Estimation Formula

```
Required instances = ceil(peak_RPS / RPS_per_instance)

RPS_per_instance (JVM mode):     ~2,000 RPS (simple CRUD)
RPS_per_instance (Native mode):  ~5,000 RPS (simple CRUD)
RPS_per_instance (with Kafka):   ~1,500 RPS (event publishing adds overhead)
RPS_per_instance (with gRPC):    ~3,000 RPS (binary protocol, efficient)
```

### Resource Planning

| Users | Instances | CPU (total) | Memory (total) | DB Connections | Kafka Partitions |
|-------|-----------|-------------|----------------|----------------|-----------------|
| 1K | 1 | 2 cores | 2 GB | 20 | 6 |
| 10K | 3 | 6 cores | 6 GB | 60 | 12 |
| 100K | 10 | 20 cores | 20 GB | 200 (PgBouncer) | 24 |
| 1M | 30+ | 60 cores | 60 GB | 500 (PgBouncer + sharding) | 48+ |
