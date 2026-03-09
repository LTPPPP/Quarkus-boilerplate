package com.example.app.tenant.resolver;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;

import java.util.Optional;

@ApplicationScoped
public class SubdomainTenantResolver implements TenantResolver {

    @Override
    public Optional<String> resolve(ContainerRequestContext ctx) {
        String host = ctx.getHeaderString("Host");
        if (host == null || host.isBlank()) {
            return Optional.empty();
        }

        host = host.split(":")[0];

        String[] parts = host.split("\\.");
        if (parts.length >= 3) {
            String subdomain = parts[0].trim().toLowerCase();
            if (!subdomain.equals("www") && !subdomain.equals("api")) {
                return Optional.of(subdomain);
            }
        }

        return Optional.empty();
    }

    @Override
    public int priority() {
        return 30;
    }
}
