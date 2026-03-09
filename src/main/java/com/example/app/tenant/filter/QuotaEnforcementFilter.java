package com.example.app.tenant.filter;

import com.example.app.exception.QuotaExceededException;
import com.example.app.tenant.context.TenantContext;
import com.example.app.tenant.service.QuotaEnforcementService;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Map;

@Provider
@Priority(Priorities.AUTHORIZATION + 10)
public class QuotaEnforcementFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(QuotaEnforcementFilter.class);

    @Inject
    QuotaEnforcementService quotaEnforcementService;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath();
        String method = requestContext.getMethod();

        if ("POST".equalsIgnoreCase(method) && path.contains("/api/v1/users")) {
            String tenantId = TenantContext.getCurrentTenant();
            if (TenantContext.DEFAULT_TENANT.equals(tenantId)) {
                return;
            }

            try {
                quotaEnforcementService.checkUserQuota(tenantId);
            } catch (QuotaExceededException e) {
                LOG.warnf("Quota enforcement blocked request: %s %s for tenant %s", method, path, tenantId);
                requestContext.abortWith(Response.status(Response.Status.TOO_MANY_REQUESTS)
                        .entity(Map.of(
                                "status", 429,
                                "error", "Quota Exceeded",
                                "message", e.getMessage(),
                                "tenantId", tenantId,
                                "timestamp", Instant.now().toString()
                        ))
                        .build());
            }
        }
    }
}
