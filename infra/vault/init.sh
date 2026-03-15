#!/bin/bash
# ============================================================
# Vault Initialization Script (Dev Mode)
# ============================================================
# This script seeds HashiCorp Vault with application secrets
# for local development. In production, use Vault UI, Terraform,
# or your organization's Vault provisioning pipeline.
#
# Usage:
#   docker-compose up -d vault
#   ./infra/vault/init.sh
# ============================================================

set -euo pipefail

VAULT_ADDR="${VAULT_ADDR:-http://localhost:8200}"
VAULT_TOKEN="${VAULT_TOKEN:-dev-root-token}"

export VAULT_ADDR VAULT_TOKEN

echo "=== Initializing Vault at ${VAULT_ADDR} ==="

# Wait for Vault to be ready
for i in $(seq 1 30); do
    if vault status > /dev/null 2>&1; then
        echo "Vault is ready."
        break
    fi
    echo "Waiting for Vault... ($i/30)"
    sleep 1
done

# Enable KV v2 secrets engine (already enabled in dev mode at 'secret/')
echo "=== Seeding application secrets ==="

vault kv put secret/myapp/config \
    db.password="postgres" \
    encryption.key="change-this-default-key-32chars!" \
    jwt.secret="your-super-secret-jwt-key-here" \
    redis.password="" \
    kafka.sasl.password=""

echo ""
echo "=== Vault secrets seeded successfully ==="
echo ""
echo "Secrets stored at: secret/myapp/config"
echo "Verify with: vault kv get secret/myapp/config"
echo ""
echo "In application.properties, these secrets are accessible as:"
echo "  \${db.password}  -> DB_PASSWORD"
echo "  \${encryption.key}  -> ENCRYPTION_KEY"
echo "  \${jwt.secret}  -> JWT_SECRET"
