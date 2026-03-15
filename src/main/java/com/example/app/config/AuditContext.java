package com.example.app.config;

/**
 * Thread-local holder for the current authenticated user identity.
 * Used by {@link com.example.app.domain.BaseEntity} to automatically
 * populate createdBy / updatedBy audit fields via JPA lifecycle callbacks.
 *
 * Set by {@link com.example.app.tenant.filter.TenantFilter} on each request
 * and cleared in the response filter.
 */
public final class AuditContext {

    public static final String SYSTEM_USER = "SYSTEM";

    private static final ThreadLocal<String> CURRENT_USER = new ThreadLocal<>();

    private AuditContext() {
    }

    public static void setCurrentUser(String userId) {
        CURRENT_USER.set(userId);
    }

    public static String getCurrentUser() {
        String user = CURRENT_USER.get();
        return user != null ? user : SYSTEM_USER;
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
