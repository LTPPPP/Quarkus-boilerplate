# Operational Runbook

## Quick Reference

| Action | Command |
|--------|---------|
| Check health | `curl http://localhost:8080/q/health` |
| View logs | `docker compose logs -f app` |
| Restart app | `docker compose restart app` |
| Scale up | `docker compose up -d --scale app=N` |
| Check Kafka lag | Kafka UI → Consumer Groups |
| View traces | Jaeger UI → http://localhost:16686 |
| Check metrics | Grafana → http://localhost:3000 |
| Retry DLQ event | `POST /api/v1/admin/dlq/{eventId}/retry` |
| Suspend tenant | `POST /api/v1/admin/tenants/{tenantId}/suspend` |

## Health Checks

### Application Health

```bash
# Combined check
curl -s http://localhost:8080/q/health | jq .

# Liveness (is the process alive?)
curl -s http://localhost:8080/q/health/live | jq .

# Readiness (can it handle requests?)
curl -s http://localhost:8080/q/health/ready | jq .

# Expected response
{
  "status": "UP",
  "checks": [
    { "name": "Database connections health check", "status": "UP" },
    { "name": "Kafka connection health check", "status": "UP" },
    { "name": "Redis connection health check", "status": "UP" },
    { "name": "gRPC Server health", "status": "UP" }
  ]
}
```

### Infrastructure Health

```bash
# PostgreSQL
docker compose exec postgres pg_isready -U postgres

# Redis
docker compose exec redis redis-cli ping

# Kafka
docker compose exec kafka kafka-topics --bootstrap-server localhost:29092 --list

# Zookeeper
docker compose exec zookeeper echo ruok | nc localhost 2181
```

## Incident Response

### Severity Levels

| Level | Definition | Response Time | Examples |
|-------|-----------|---------------|----------|
| SEV1 | Service down, all users affected | 15 min | App crash loop, DB unreachable |
| SEV2 | Major feature broken | 30 min | Kafka down, payments failing |
| SEV3 | Minor feature broken | 2 hours | One tenant degraded, DLQ growing |
| SEV4 | Cosmetic/non-urgent | Next business day | Slow queries, warning logs |

### SEV1: Application Not Starting

**Symptoms:** Health check returns 503, pods in CrashLoopBackOff

**Diagnosis:**
```bash
# Check application logs
docker compose logs app --tail 100
# or
kubectl logs -l app=myapp --tail=100

# Common causes:
# 1. Database unreachable → check DB connectivity
# 2. Flyway migration failed → check migration SQL
# 3. Out of memory → check resource limits
# 4. Port conflict → check ports 8080/9000
```

**Resolution:**
```bash
# If DB migration failed
# 1. Check flyway_schema_history table for failed migration
docker compose exec postgres psql -U postgres -d appdb \
  -c "SELECT * FROM flyway_schema_history WHERE success = false;"

# 2. Fix the migration SQL
# 3. Either repair Flyway or manually fix the schema
docker compose exec postgres psql -U postgres -d appdb \
  -c "DELETE FROM flyway_schema_history WHERE success = false;"

# If out of memory, increase limits
# In docker-compose: deploy.resources.limits.memory: 4g
# In K8s: resources.limits.memory: 4Gi
```

### SEV2: Kafka Consumer Lag Growing

**Symptoms:** Events not being processed, consumer lag > 10K in Kafka UI

**Diagnosis:**
```bash
# Check consumer group status
docker compose exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:29092 \
  --describe --group user-service-group

# Check consumer logs for errors
docker compose logs app 2>&1 | grep -i "kafka\|consumer\|ERROR"
```

**Resolution:**
```bash
# If consumer is stuck on a bad message
# 1. Check DLQ for failed events
curl -s http://localhost:8080/api/v1/admin/dlq | jq .

# 2. Skip the bad message by advancing offset
docker compose exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:29092 \
  --group user-service-group \
  --topic user-events \
  --reset-offsets --shift-by 1 \
  --execute

# If consumer is slow, scale up instances
docker compose up -d --scale app=5
```

### SEV2: Database Connection Pool Exhausted

**Symptoms:** "Unable to acquire JDBC Connection" errors

**Diagnosis:**
```bash
# Check active connections
docker compose exec postgres psql -U postgres -d appdb \
  -c "SELECT count(*) FROM pg_stat_activity WHERE datname = 'appdb';"

# Check waiting queries
docker compose exec postgres psql -U postgres -d appdb \
  -c "SELECT pid, now() - pg_stat_activity.query_start AS duration, query, state
      FROM pg_stat_activity
      WHERE datname = 'appdb' AND state != 'idle'
      ORDER BY duration DESC;"
```

**Resolution:**
```bash
# Kill long-running queries (> 5 min)
docker compose exec postgres psql -U postgres -d appdb \
  -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity
      WHERE datname = 'appdb'
      AND state = 'active'
      AND now() - query_start > interval '5 minutes';"

# Increase pool size (short term)
# Set DB_MAX_POOL_SIZE=50 in .env, restart app

# Add PgBouncer (long term, see SCALING.md)
```

### SEV3: Tenant Provisioning Failure

**Symptoms:** Tenant stuck in PROVISIONING status

**Diagnosis:**
```bash
# Check tenant status
docker compose exec postgres psql -U postgres -d appdb \
  -c "SELECT tenant_id, status, created_at FROM tenants WHERE status = 'PROVISIONING';"

# Check if schema was created
docker compose exec postgres psql -U postgres -d appdb \
  -c "SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE 'tenant_%';"

# Check application logs for provisioning errors
docker compose logs app 2>&1 | grep -i "provision\|migration\|flyway"
```

**Resolution:**
```bash
# If schema was created but migration failed
# 1. Drop the partially created schema
docker compose exec postgres psql -U postgres -d appdb \
  -c "DROP SCHEMA IF EXISTS tenant_<tenantId> CASCADE;"

# 2. Reset tenant status to allow retry
docker compose exec postgres psql -U postgres -d appdb \
  -c "DELETE FROM tenants WHERE tenant_id = '<tenantId>' AND status = 'PROVISIONING';"

# 3. Retry provisioning via API
curl -X POST http://localhost:8080/api/v1/admin/tenants \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"tenantId": "<tenantId>", "name": "...", "plan": "..."}'
```

### SEV3: Dead Letter Queue Growing

**Symptoms:** DLQ topic has accumulating messages

**Diagnosis:**
```bash
# List DLQ events
curl -s http://localhost:8080/api/v1/admin/dlq | jq .

# Check specific event
curl -s http://localhost:8080/api/v1/admin/dlq/{eventId} | jq .
# Look at: original topic, error message, retry count
```

**Resolution:**
```bash
# Fix the root cause first (bad data, missing dependency, etc.)

# Retry a single event
curl -X POST http://localhost:8080/api/v1/admin/dlq/{eventId}/retry

# Bulk retry all DLQ events (use with caution)
# Script: iterate over DLQ events and retry each
curl -s http://localhost:8080/api/v1/admin/dlq | \
  jq -r '.[].id' | \
  xargs -I{} curl -X POST http://localhost:8080/api/v1/admin/dlq/{}/retry
```

## Rollback Procedures

### Application Rollback

```bash
# Docker Compose
docker compose pull app  # pull previous tag
docker compose up -d app

# Kubernetes
kubectl rollout undo deployment/myapp -n appdb-production

# Check rollout status
kubectl rollout status deployment/myapp -n appdb-production

# Rollback to specific revision
kubectl rollout history deployment/myapp -n appdb-production
kubectl rollout undo deployment/myapp --to-revision=N -n appdb-production
```

### Database Rollback

**Flyway does not support automatic rollback.** For each migration, maintain a manual rollback script.

```bash
# Example: rollback V5__add_feature_flags.sql
docker compose exec postgres psql -U postgres -d appdb \
  -f rollback/R5__undo_add_feature_flags.sql

# Update Flyway history
docker compose exec postgres psql -U postgres -d appdb \
  -c "DELETE FROM flyway_schema_history WHERE version = '5';"
```

### Kafka Topic Rollback

```bash
# Reset consumer group to specific time
docker compose exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:29092 \
  --group order-service-group \
  --topic order-events \
  --reset-offsets --to-datetime 2024-01-15T10:00:00.000 \
  --execute
```

## Maintenance Procedures

### Database Maintenance

```bash
# Analyze tables (update query planner statistics)
docker compose exec postgres psql -U postgres -d appdb \
  -c "ANALYZE;"

# Vacuum (reclaim dead tuples)
docker compose exec postgres psql -U postgres -d appdb \
  -c "VACUUM ANALYZE;"

# Check table bloat
docker compose exec postgres psql -U postgres -d appdb \
  -c "SELECT relname, n_live_tup, n_dead_tup,
      round(n_dead_tup::numeric / greatest(n_live_tup, 1) * 100, 2) as dead_pct
      FROM pg_stat_user_tables ORDER BY n_dead_tup DESC LIMIT 20;"
```

### Redis Maintenance

```bash
# Check memory usage
docker compose exec redis redis-cli INFO memory

# Check key count
docker compose exec redis redis-cli DBSIZE

# Flush tenant cache (specific tenant)
docker compose exec redis redis-cli KEYS "acme:*" | xargs redis-cli DEL

# Flush all caches (use with caution)
docker compose exec redis redis-cli FLUSHDB
```

### Kafka Maintenance

```bash
# List topics
docker compose exec kafka kafka-topics --bootstrap-server localhost:29092 --list

# Describe topic
docker compose exec kafka kafka-topics --bootstrap-server localhost:29092 \
  --describe --topic order-events

# Check consumer groups
docker compose exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:29092 --list

# Delete old messages (set retention)
docker compose exec kafka kafka-configs --bootstrap-server localhost:29092 \
  --alter --entity-type topics --entity-name dead-letter-queue \
  --add-config retention.ms=604800000  # 7 days
```

### Log Management

```bash
# View application logs with filtering
docker compose logs app 2>&1 | grep "ERROR\|WARN" | tail -50

# Filter by tenant
docker compose logs app 2>&1 | grep "tenant=acme" | tail -20

# Export logs for analysis
docker compose logs app > /tmp/appdb-logs-$(date +%Y%m%d).log

# In Kubernetes
kubectl logs -l app=myapp --all-containers --since=1h > /tmp/k8s-logs.log
```

## Monitoring Dashboards

### Grafana Access

- URL: http://localhost:3000 (or your Grafana host)
- Default credentials: admin / admin
- Pre-configured dashboard: "Quarkus Enterprise Boilerplate - Metrics"

### Key Dashboard Panels

| Panel | What to Look For |
|-------|-----------------|
| HTTP Request Rate | Sudden drops (service degradation) or spikes (DoS) |
| HTTP Response Time (p95) | Trending up = performance regression |
| JVM Heap Memory | Approaching max = OOM risk |
| Kafka Consumer Lag | Growing = consumers can't keep up |
| gRPC Request Rate | Error codes increasing |
| DB Connection Pool | Available approaching 0 = pool exhaustion |
| System CPU | Sustained > 80% = need to scale |

### Alerting Rules (Prometheus)

```yaml
# infra/prometheus/alerts.yml
groups:
- name: appdb-alerts
  rules:
  - alert: HighErrorRate
    expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) / rate(http_server_requests_seconds_count[5m]) > 0.05
    for: 5m
    labels:
      severity: critical
    annotations:
      summary: "Error rate > 5% for 5 minutes"

  - alert: HighLatency
    expr: histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m])) > 2
    for: 10m
    labels:
      severity: warning
    annotations:
      summary: "p95 latency > 2s for 10 minutes"

  - alert: KafkaConsumerLag
    expr: kafka_consumer_fetch_manager_records_lag > 10000
    for: 15m
    labels:
      severity: warning
    annotations:
      summary: "Kafka consumer lag > 10K for 15 minutes"

  - alert: DatabaseConnectionPoolExhausted
    expr: agroal_available_count < 2
    for: 2m
    labels:
      severity: critical
    annotations:
      summary: "DB connection pool nearly exhausted"
```

## Emergency Contacts

| Role | Contact | Escalation |
|------|---------|------------|
| On-Call Engineer | (rotate) | PagerDuty / Slack #oncall |
| Platform Lead | TBD | SEV1 + SEV2 |
| DBA | TBD | Database incidents |
| Security | TBD | Security incidents |
