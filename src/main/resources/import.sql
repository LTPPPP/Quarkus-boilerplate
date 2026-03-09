-- Dev/Test seed data (only used when quarkus.hibernate-orm.database.generation=drop-and-create)

-- Default public tenant
INSERT INTO tenants (id, tenant_id, name, status, plan, schema_name, max_users, storage_quota_gb, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'default', 'Default Tenant', 'ACTIVE', 'ENTERPRISE', 'public', 1000, 100, NOW(), NOW())
ON CONFLICT (tenant_id) DO NOTHING;

-- Demo tenant
INSERT INTO tenants (id, tenant_id, name, status, plan, schema_name, max_users, storage_quota_gb, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000002', 'demo', 'Demo Tenant', 'ACTIVE', 'STARTER', 'tenant_demo', 50, 10, NOW(), NOW())
ON CONFLICT (tenant_id) DO NOTHING;

-- Admin user for default tenant
INSERT INTO users (id, email, password_hash, full_name, role, status, tenant_id, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000010', 'admin@example.com',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        'System Admin', 'SUPER_ADMIN', 'ACTIVE', 'default', NOW(), NOW())
ON CONFLICT (email) DO NOTHING;

-- Demo user
INSERT INTO users (id, email, password_hash, full_name, role, status, tenant_id, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000011', 'user@demo.example.com',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        'Demo User', 'USER', 'ACTIVE', 'demo', NOW(), NOW())
ON CONFLICT (email) DO NOTHING;
