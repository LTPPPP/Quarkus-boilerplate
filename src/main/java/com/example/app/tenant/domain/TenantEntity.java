package com.example.app.tenant.domain;

import com.example.app.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "tenants")
public class TenantEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, unique = true, length = 30)
    private String tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TenantStatus status = TenantStatus.PROVISIONING;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false)
    private TenantPlan plan = TenantPlan.FREE;

    @Column(name = "schema_name")
    private String schemaName;

    @Column(name = "db_url")
    private String dbUrl;

    @Column(name = "db_username")
    private String dbUsername;

    @Column(name = "db_password_encrypted")
    private String dbPasswordEncrypted;

    @Column(name = "max_users", nullable = false)
    private int maxUsers = 10;

    @Column(name = "storage_quota_gb", nullable = false)
    private int storageQuotaGb = 1;

    @Column(name = "features", columnDefinition = "TEXT")
    private String features;

    public enum TenantStatus {
        ACTIVE, SUSPENDED, PROVISIONING, DELETED
    }

    public enum TenantPlan {
        FREE, STARTER, PROFESSIONAL, ENTERPRISE
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TenantStatus getStatus() {
        return status;
    }

    public void setStatus(TenantStatus status) {
        this.status = status;
    }

    public TenantPlan getPlan() {
        return plan;
    }

    public void setPlan(TenantPlan plan) {
        this.plan = plan;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public String getDbUrl() {
        return dbUrl;
    }

    public void setDbUrl(String dbUrl) {
        this.dbUrl = dbUrl;
    }

    public String getDbUsername() {
        return dbUsername;
    }

    public void setDbUsername(String dbUsername) {
        this.dbUsername = dbUsername;
    }

    public String getDbPasswordEncrypted() {
        return dbPasswordEncrypted;
    }

    public void setDbPasswordEncrypted(String dbPasswordEncrypted) {
        this.dbPasswordEncrypted = dbPasswordEncrypted;
    }

    public int getMaxUsers() {
        return maxUsers;
    }

    public void setMaxUsers(int maxUsers) {
        this.maxUsers = maxUsers;
    }

    public int getStorageQuotaGb() {
        return storageQuotaGb;
    }

    public void setStorageQuotaGb(int storageQuotaGb) {
        this.storageQuotaGb = storageQuotaGb;
    }

    public String getFeatures() {
        return features;
    }

    public void setFeatures(String features) {
        this.features = features;
    }
}
