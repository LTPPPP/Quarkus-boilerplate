package com.example.app.tenant.resolver;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;

import org.jboss.logging.Logger;

import java.util.Optional;

@ApplicationScoped
public class QueryParamTenantResolver implements TenantResolver {

    private static final Logger LOG = Logger.getLogger(QueryParamTenantResolver.class);
    private static final String PARAM_NAME = "tenant";

    @Override
    public Optional<String> resolve(ContainerRequestContext ctx) {
        String tenantId = ctx.getUriInfo().getQueryParameters().getFirst(PARAM_NAME);
        if (tenantId != null && !tenantId.isBlank()) {
            LOG.debugf("Tenant resolved from query param: %s", tenantId);
            return Optional.of(tenantId.trim().toLowerCase());
        }
        return Optional.empty();
    }

    @Override
    public int priority() {
        return 50;
    }
}
