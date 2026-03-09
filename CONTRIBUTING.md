# Contributing

## Development Setup

```bash
# Prerequisites
java -version   # Java 21+
mvn -version    # Maven 3.9+ (or use ./mvnw)
docker --version

# Start infrastructure
docker compose up -d postgres redis zookeeper kafka schema-registry

# Run in dev mode
./mvnw quarkus:dev
```

## Branching Strategy

We use **trunk-based development** with short-lived feature branches.

```
main (production)
 ├── release/1.x.x        ← release candidates, hotfixes cherry-picked
 ├── feature/JIRA-123-...  ← feature work (< 3 days)
 ├── fix/JIRA-456-...      ← bug fixes
 └── chore/...             ← tooling, deps, CI changes
```

**Rules:**
- `main` is always deployable
- Feature branches live max 3 days — break large work into smaller PRs
- All PRs require at least 1 approval + passing CI
- Squash merge to `main` for clean history
- Delete branches after merge

## Commit Convention

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <short summary>

<optional body>

<optional footer>
```

**Types:** `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `chore`, `ci`, `build`

**Scopes:** `kafka`, `tenant`, `grpc`, `domain`, `api`, `config`, `infra`, `deps`

**Examples:**
```
feat(kafka): add payment event consumer with idempotency check
fix(tenant): resolve schema leak when tenant provisioning fails
refactor(grpc): extract common error mapping to base class
perf(db): add composite index on orders(user_id, status)
test(tenant): add row-level security isolation test
docs(scaling): add Kafka partition scaling guide
chore(deps): bump Quarkus to 3.18.0
```

## Code Standards

### Java Style

- **Java 21** — use records, sealed classes, pattern matching where appropriate
- **No Lombok in new code** — use records or explicit getters/setters (existing Lombok code is fine)
- **Inject via constructor** or `@Inject` field injection (Quarkus CDI)
- **No `null` returns** — use `Optional<T>` for nullable results
- **Fail fast** — validate inputs at service layer boundaries, throw domain exceptions

### Package Conventions

```
com.example.app.
├── config/         # @Singleton, @ApplicationScoped configuration beans
├── domain/         # JPA @Entity classes (no business logic)
├── repository/     # PanacheRepository interfaces (data access only)
├── service/        # Business logic (transaction boundaries here)
├── resource/       # REST endpoints (thin: validate → delegate → respond)
├── exception/      # Domain exceptions + global mapper
├── util/           # Pure utility functions (stateless)
├── event/          # Kafka event domain, producers, consumers, handlers
├── tenant/         # Multi-tenancy infrastructure
└── grpc/           # gRPC services, interceptors, mappers, clients
```

### Layer Rules

| Layer | Can Depend On | Cannot Depend On |
|-------|--------------|-------------------|
| `resource/` | `service/`, `exception/` | `repository/`, `event/producer/` |
| `grpc/service/` | `service/`, `grpc/mapper/` | `repository/` directly |
| `service/` | `repository/`, `event/producer/`, `grpc/client/` | `resource/`, `grpc/service/` |
| `repository/` | `domain/` | anything else |
| `event/consumer/` | `event/handler/`, `tenant/context/` | `service/` directly |
| `event/handler/` | `service/`, `grpc/client/` | `event/consumer/` |

### Testing

- **Unit tests** for services and handlers: mock repositories, mock event producers
- **Integration tests** with `@QuarkusTest`: real DB (testcontainers), in-memory Kafka
- **Test naming**: `should<Action>_when<Condition>` (e.g., `shouldRejectUser_whenEmailDuplicate`)
- Maintain test isolation: each test class manages its own data
- Use `%test` profile overrides for in-memory connectors

```bash
# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=TenantFilterTest

# Run with coverage
./mvnw test jacoco:report
# Report at target/site/jacoco/index.html
```

### Database Migrations

- **Public schema changes** → `src/main/resources/db/migration/V{N}__description.sql`
- **Per-tenant schema changes** → `src/main/resources/db/tenant-migrations/V{N}__description.sql`
- **Never** modify existing migration files — always create a new version
- **Always** test migrations against a copy of production data before deploying
- Include both UP migration (Flyway handles this) and document the rollback SQL in comments

### Adding a New Module

When you need to add a completely new domain (e.g., `notification/`, `inventory/`):

1. Create the package structure: `domain/`, `repository/`, `service/`, `resource/`
2. Create the JPA entity extending `BaseEntity` (or `TenantAwareEntity`)
3. Add Flyway migration (both `migration/` and `tenant-migrations/`)
4. Create events if the module participates in event-driven flows
5. Create proto + gRPC service if inter-service communication is needed
6. Add REST endpoints
7. Write tests (unit + integration)
8. Update `README.md` module overview

## PR Checklist

Before submitting a PR, verify:

- [ ] Code compiles: `./mvnw compile`
- [ ] All tests pass: `./mvnw test`
- [ ] No new linter warnings
- [ ] Database migrations are forwards-compatible (no breaking schema changes)
- [ ] New `application.properties` entries have `%test` overrides
- [ ] New Kafka channels have `%test.*.connector=smallrye-in-memory` override
- [ ] New REST endpoints have OpenAPI annotations (`@Tag`, `@Operation`)
- [ ] Sensitive data is not logged or exposed in API responses
- [ ] PR description includes: what changed, why, how to test

## Dependency Management

- All Quarkus extensions are managed by the Quarkus BOM — do **not** pin versions manually
- Third-party libraries: pin exact versions in `pom.xml`
- Run `./mvnw versions:display-dependency-updates` to check for updates
- Update dependencies in a separate `chore(deps)` PR, never mixed with feature work

## IDE Setup

### IntelliJ IDEA
- Install Quarkus plugin
- Enable annotation processing (Settings → Build → Compiler → Annotation Processors)
- Import as Maven project
- Set SDK to Java 21

### VS Code
- Install Extension Pack for Java
- Install Quarkus extension
- `settings.json` auto-configured via `.vscode/`
