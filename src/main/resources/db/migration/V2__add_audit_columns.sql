-- ============================================================
-- V2: Add audit columns (created_by, updated_by) to all tables
-- ============================================================

ALTER TABLE tenants ADD COLUMN IF NOT EXISTS created_by VARCHAR(255) DEFAULT 'SYSTEM';
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255) DEFAULT 'SYSTEM';

ALTER TABLE tenant_configs ADD COLUMN IF NOT EXISTS created_by VARCHAR(255) DEFAULT 'SYSTEM';
ALTER TABLE tenant_configs ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255) DEFAULT 'SYSTEM';

ALTER TABLE users ADD COLUMN IF NOT EXISTS created_by VARCHAR(255) DEFAULT 'SYSTEM';
ALTER TABLE users ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255) DEFAULT 'SYSTEM';

ALTER TABLE orders ADD COLUMN IF NOT EXISTS created_by VARCHAR(255) DEFAULT 'SYSTEM';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255) DEFAULT 'SYSTEM';

ALTER TABLE order_items ADD COLUMN IF NOT EXISTS created_by VARCHAR(255) DEFAULT 'SYSTEM';
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255) DEFAULT 'SYSTEM';

ALTER TABLE payments ADD COLUMN IF NOT EXISTS created_by VARCHAR(255) DEFAULT 'SYSTEM';
ALTER TABLE payments ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255) DEFAULT 'SYSTEM';

ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS created_by VARCHAR(255) DEFAULT 'SYSTEM';
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255) DEFAULT 'SYSTEM';

ALTER TABLE dead_letter_events ADD COLUMN IF NOT EXISTS created_by VARCHAR(255) DEFAULT 'SYSTEM';
ALTER TABLE dead_letter_events ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255) DEFAULT 'SYSTEM';

-- Indexes for audit trail queries
CREATE INDEX IF NOT EXISTS idx_users_created_by ON users(created_by);
CREATE INDEX IF NOT EXISTS idx_users_updated_by ON users(updated_by);
CREATE INDEX IF NOT EXISTS idx_orders_created_by ON orders(created_by);
CREATE INDEX IF NOT EXISTS idx_orders_updated_by ON orders(updated_by);
CREATE INDEX IF NOT EXISTS idx_payments_created_by ON payments(created_by);
CREATE INDEX IF NOT EXISTS idx_payments_updated_by ON payments(updated_by);
