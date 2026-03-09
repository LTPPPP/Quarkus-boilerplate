# Deployment Guide

## Deployment Environments

| Environment | Purpose | URL | Branch |
|-------------|---------|-----|--------|
| Local | Development | localhost:8080 | any |
| CI | Automated testing | ephemeral | PR branches |
| Staging | Pre-production validation | staging.your-domain.internal | main |
| Production | Live traffic | api.your-domain.example.com | release/* |

## Build Pipeline

### CI/CD Flow

```
┌─────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌────────────┐
│  Code   │──►│  Build   │──►│  Test    │──►│  Scan    │──►│  Publish   │
│  Push   │   │  (Maven) │   │  (Unit + │   │  (SAST + │   │  (Docker   │
│         │   │          │   │   Integ) │   │   Deps)  │   │   Image)   │
└─────────┘   └──────────┘   └──────────┘   └──────────┘   └──────┬─────┘
                                                                    │
              ┌──────────┐   ┌──────────┐   ┌──────────┐          │
              │Production│◄──│  Staging  │◄──│  Deploy  │◄─────────┘
              │  Deploy  │   │  Verify   │   │  Staging │
              │ (manual) │   │  (smoke)  │   │  (auto)  │
              └──────────┘   └──────────┘   └──────────┘
```

### GitHub Actions Example

```yaml
# .github/workflows/ci.yml
name: CI/CD Pipeline

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}
  JAVA_VERSION: '21'

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16-alpine
        env:
          POSTGRES_DB: appdb_test
          POSTGRES_USER: test
          POSTGRES_PASSWORD: test
        ports: ['5432:5432']
        options: >-
          --health-cmd "pg_isready -U test"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
      redis:
        image: redis:7-alpine
        ports: ['6379:6379']
        options: >-
          --health-cmd "redis-cli ping"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

    steps:
    - uses: actions/checkout@v4

    - name: Set up Java
      uses: actions/setup-java@v4
      with:
        distribution: 'temurin'
        java-version: ${{ env.JAVA_VERSION }}
        cache: 'maven'

    - name: Build
      run: ./mvnw compile -B

    - name: Test
      run: ./mvnw test -B
      env:
        DB_HOST: localhost
        DB_PORT: 5432
        DB_NAME: appdb_test
        DB_USER: test
        DB_PASSWORD: test

    - name: Package
      run: ./mvnw package -DskipTests -B

    - name: Upload test results
      if: always()
      uses: actions/upload-artifact@v4
      with:
        name: test-results
        path: target/surefire-reports/

  security-scan:
    runs-on: ubuntu-latest
    needs: build-and-test
    steps:
    - uses: actions/checkout@v4

    - name: Dependency check (OWASP)
      run: ./mvnw org.owasp:dependency-check-maven:check -B

    - name: Trivy scan (Docker image)
      uses: aquasecurity/trivy-action@master
      with:
        scan-type: 'fs'
        scan-ref: '.'
        format: 'sarif'
        output: 'trivy-results.sarif'

  docker-build:
    runs-on: ubuntu-latest
    needs: [build-and-test, security-scan]
    if: github.ref == 'refs/heads/main'
    permissions:
      contents: read
      packages: write

    steps:
    - uses: actions/checkout@v4

    - name: Set up Java
      uses: actions/setup-java@v4
      with:
        distribution: 'temurin'
        java-version: ${{ env.JAVA_VERSION }}
        cache: 'maven'

    - name: Build application
      run: ./mvnw package -DskipTests -B

    - name: Login to Container Registry
      uses: docker/login-action@v3
      with:
        registry: ${{ env.REGISTRY }}
        username: ${{ github.actor }}
        password: ${{ secrets.GITHUB_TOKEN }}

    - name: Build and push Docker image
      uses: docker/build-push-action@v5
      with:
        context: .
        file: src/main/docker/Dockerfile.jvm
        push: true
        tags: |
          ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:${{ github.sha }}
          ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:latest

  deploy-staging:
    runs-on: ubuntu-latest
    needs: docker-build
    environment: staging
    steps:
    - name: Deploy to staging
      run: |
        # kubectl or helm deploy command
        echo "Deploying ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:${{ github.sha }} to staging"

  deploy-production:
    runs-on: ubuntu-latest
    needs: deploy-staging
    environment: production  # requires manual approval
    steps:
    - name: Deploy to production
      run: |
        echo "Deploying to production"
```

## Docker Deployment

### Build

```bash
# JVM mode
./mvnw package -DskipTests
docker build -f src/main/docker/Dockerfile.jvm -t myapp:latest .

# Native mode (requires GraalVM or Docker multi-stage)
./mvnw package -Dnative -DskipTests -Dquarkus.native.container-build=true
docker build -f src/main/docker/Dockerfile.native-micro -t myapp:native .
```

### Docker Compose (Full Stack)

```bash
# Start everything
docker compose up -d

# Scale application
docker compose up -d --scale app=3

# View logs
docker compose logs -f app

# Stop
docker compose down

# Stop and remove volumes
docker compose down -v
```

## Kubernetes Deployment

### Namespace Setup

```bash
kubectl create namespace myapp-staging
kubectl create namespace myapp-production
```

### Secrets

```bash
# Create secrets (never commit these)
kubectl create secret generic myapp-db \
  --namespace myapp-production \
  --from-literal=DB_HOST=prod-db.internal \
  --from-literal=DB_USER=myapp \
  --from-literal=DB_PASSWORD=$(openssl rand -base64 32)

kubectl create secret generic myapp-encryption \
  --namespace myapp-production \
  --from-literal=ENCRYPTION_KEY=$(openssl rand -base64 32)

kubectl create secret generic myapp-jwt \
  --namespace myapp-production \
  --from-file=publicKey.pem=certs/publicKey.pem \
  --from-file=privateKey.pem=certs/privateKey.pem
```

### Deployment Manifest

```yaml
# k8s/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: myapp
  labels:
    app: myapp
    version: v1
spec:
  replicas: 3
  selector:
    matchLabels:
      app: myapp
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  template:
    metadata:
      labels:
        app: myapp
        version: v1
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/path: "/q/metrics"
        prometheus.io/port: "8080"
    spec:
      serviceAccountName: myapp
      terminationGracePeriodSeconds: 30
      containers:
      - name: myapp
        image: ghcr.io/org/myapp:latest
        ports:
        - containerPort: 8080
          name: http
          protocol: TCP
        - containerPort: 9000
          name: grpc
          protocol: TCP
        envFrom:
        - configMapRef:
            name: myapp-config
        - secretRef:
            name: myapp-db
        - secretRef:
            name: myapp-encryption
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
          timeoutSeconds: 3
          failureThreshold: 3
        livenessProbe:
          httpGet:
            path: /q/health/live
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 20
          timeoutSeconds: 5
          failureThreshold: 3
        startupProbe:
          httpGet:
            path: /q/health/started
            port: 8080
          failureThreshold: 30
          periodSeconds: 2
        lifecycle:
          preStop:
            exec:
              command: ["/bin/sh", "-c", "sleep 5"]
---
apiVersion: v1
kind: Service
metadata:
  name: myapp
spec:
  selector:
    app: myapp
  ports:
  - name: http
    port: 80
    targetPort: 8080
  - name: grpc
    port: 9000
    targetPort: 9000
  type: ClusterIP
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: myapp-config
data:
  QUARKUS_HTTP_PORT: "8080"
  GRPC_PORT: "9000"
  REDIS_HOST: "redis-master.myapp.svc.cluster.local"
  REDIS_PORT: "6379"
  KAFKA_BOOTSTRAP_SERVERS: "kafka-0.kafka.myapp.svc.cluster.local:9092"
  OTEL_EXPORTER_OTLP_ENDPOINT: "http://jaeger-collector.observability:4317"
  MULTITENANCY_STRATEGY: "SCHEMA"
```

### Helm Chart (Recommended)

```bash
# Structure
helm/myapp/
├── Chart.yaml
├── values.yaml
├── values-staging.yaml
├── values-production.yaml
└── templates/
    ├── deployment.yaml
    ├── service.yaml
    ├── configmap.yaml
    ├── secret.yaml
    ├── hpa.yaml
    ├── pdb.yaml
    └── ingress.yaml
```

```bash
# Deploy to staging
helm upgrade --install myapp helm/myapp \
  --namespace myapp-staging \
  --values helm/myapp/values-staging.yaml \
  --set image.tag=$GIT_SHA

# Deploy to production
helm upgrade --install myapp helm/myapp \
  --namespace myapp-production \
  --values helm/myapp/values-production.yaml \
  --set image.tag=$GIT_SHA
```

## Zero-Downtime Deployment

### Rolling Update Strategy

1. New pods start alongside old pods
2. New pods pass startup probe
3. New pods pass readiness probe → receive traffic
4. Old pods receive SIGTERM
5. Old pods stop accepting new requests (preStop hook: 5s grace)
6. Old pods finish in-flight requests (terminationGracePeriodSeconds: 30s)
7. Old pods terminate

### Database Migration Strategy

Flyway migrations run at application startup. For zero-downtime:

**Rule:** All migrations must be backwards-compatible.

| Change | Safe? | How |
|--------|-------|-----|
| Add column (nullable) | Yes | Direct migration |
| Add column (non-null) | No | Add nullable → backfill → add constraint |
| Remove column | No | Stop using → deploy → remove in next release |
| Rename column | No | Add new → copy data → deploy → drop old |
| Add index | Yes | `CREATE INDEX CONCURRENTLY` |
| Drop table | No | Stop using → deploy → drop in next release |

**Expand-Contract pattern for breaking changes:**
1. **Expand**: Add new column/table, deploy code that writes to both old and new
2. **Migrate**: Backfill data from old to new
3. **Contract**: Deploy code that only uses new, drop old column/table

### Kafka Consumer Rebalancing

When scaling up/down, Kafka consumer groups automatically rebalance. Ensure:
- `session.timeout.ms` is reasonable (30s default)
- `max.poll.interval.ms` allows for slow handlers (5min default)
- Offset commits happen after successful processing (manual commit)

## Production Checklist

### Before First Deploy

- [ ] All secrets stored in secret manager (not env files)
- [ ] JWT keys are production keys (not dev keys)
- [ ] ENCRYPTION_KEY is unique, 32+ chars, stored securely
- [ ] DB passwords are strong, rotated regularly
- [ ] TLS enabled for all external-facing endpoints
- [ ] gRPC TLS configured for production
- [ ] Kafka SSL/SASL configured
- [ ] Redis password set
- [ ] Health checks configured and tested
- [ ] Resource limits set for all containers
- [ ] PodDisruptionBudget configured (minAvailable: 2)
- [ ] HPA configured with appropriate thresholds
- [ ] Log aggregation configured (ELK, Loki, CloudWatch)
- [ ] Alerting configured for critical metrics
- [ ] Backup strategy for PostgreSQL (daily snapshots + WAL archiving)
- [ ] Disaster recovery plan documented and tested
- [ ] CORS configured to only allow known origins
- [ ] Rate limiting enabled
- [ ] OpenTelemetry sampling configured (10% for production)

### Per-Deploy Verification

- [ ] All tests pass in CI
- [ ] Security scan clean
- [ ] Staging deploy successful
- [ ] Smoke tests pass on staging
- [ ] No breaking schema changes (or expand-contract in place)
- [ ] Monitoring dashboards reviewed
- [ ] Rollback plan confirmed
