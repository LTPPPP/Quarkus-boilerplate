package com.example.app.exception;

public class QuotaExceededException extends RuntimeException {

    private final String tenantId;
    private final String quotaType;

    public QuotaExceededException(String tenantId, String quotaType) {
        super(String.format("Quota exceeded for tenant '%s': %s", tenantId, quotaType));
        this.tenantId = tenantId;
        this.quotaType = quotaType;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getQuotaType() {
        return quotaType;
    }
}
