package com.example.app.tenant.domain;

import com.example.app.domain.BaseEntity;
import com.example.app.tenant.context.TenantContext;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;

@MappedSuperclass
public abstract class TenantAwareEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @PrePersist
    protected void onTenantAwareCreate() {
        if (this.tenantId == null || this.tenantId.isBlank()) {
            this.tenantId = TenantContext.getCurrentTenant();
        }
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
