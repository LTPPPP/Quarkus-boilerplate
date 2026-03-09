package com.example.app.tenant.resolver;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

@ApplicationScoped
public class HeaderTenantResolver implements TenantResolver {

    private final String headerName;

    public HeaderTenantResolver(
            @ConfigProperty(name = "app.multitenancy.tenant-header", defaultValue = "X-Tenant-ID") String headerName) {
        this.headerName = headerName;
    }

    @Override
    public Optional<String> resolve(ContainerRequestContext ctx) {
        String tenantId = ctx.getHeaderString(headerName);
        if (tenantId != null && !tenantId.isBlank()) {
            return Optional.of(tenantId.trim().toLowerCase());
        }
        return Optional.empty();
    }

    @Override
    public int priority() {
        return 20;
    }
}
