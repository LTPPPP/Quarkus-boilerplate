package com.example.app.tenant.context;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class TenantContext {

    public static final String DEFAULT_TENANT = "public";

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    public static String getCurrentTenant() {
        String tenant = CURRENT_TENANT.get();
        return tenant != null ? tenant : DEFAULT_TENANT;
    }

    public static void setCurrentTenant(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }

    public static boolean isValid() {
        String tenant = CURRENT_TENANT.get();
        return tenant != null && !tenant.isBlank() && tenant.matches("^[a-z0-9_-]+$");
    }

    public static boolean isSet() {
        return CURRENT_TENANT.get() != null;
    }
}
