package com.example.app.tenant.filter;

import com.example.app.config.AuditContext;
import com.example.app.exception.UnauthorizedException;
import com.example.app.tenant.context.TenantContext;
import com.example.app.tenant.domain.TenantEntity;
import com.example.app.tenant.domain.TenantRepository;
import com.example.app.tenant.resolver.TenantResolverChain;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;

@Provider
@Priority(Priorities.AUTHENTICATION - 10)
public class TenantFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger LOG = Logger.getLogger(TenantFilter.class);

    @Inject
    TenantResolverChain tenantResolverChain;

    @Inject
    TenantRepository tenantRepository;

    @Context
    UriInfo uriInfo;

    private final List<String> publicPaths;

    public TenantFilter(
            @ConfigProperty(name = "app.multitenancy.public-paths",
                    defaultValue = "/api/v1/auth,/api/v1/public,/q/health,/q/metrics,/q/openapi") String paths) {
        this.publicPaths = Arrays.asList(paths.split(","));
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath();

        if (isPublicPath(path)) {
            TenantContext.setCurrentTenant(TenantContext.DEFAULT_TENANT);
            return;
        }

        try {
            String tenantId = tenantResolverChain.resolve(requestContext);

            TenantEntity tenant = tenantRepository.findByTenantId(tenantId).orElse(null);
            if (tenant == null) {
                throw new UnauthorizedException("Tenant not found: " + tenantId);
            }

            if (tenant.getStatus() != TenantEntity.TenantStatus.ACTIVE) {
                throw new UnauthorizedException("Tenant is not active: " + tenantId +
                        " (status: " + tenant.getStatus() + ")");
            }

            TenantContext.setCurrentTenant(tenantId);
            MDC.put("tenantId", tenantId);

            // Extract authenticated user from SecurityContext for audit trail
            resolveAuditUser(requestContext);

            LOG.debugf("Tenant context set: %s for path: %s", tenantId, path);
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            LOG.errorf(e, "Error resolving tenant for path: %s", path);
            throw new UnauthorizedException("Tenant resolution failed: " + e.getMessage());
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        TenantContext.clear();
        AuditContext.clear();
        MDC.remove("tenantId");
        MDC.remove("userId");
    }

    private void resolveAuditUser(ContainerRequestContext requestContext) {
        SecurityContext securityContext = requestContext.getSecurityContext();
        if (securityContext != null) {
            Principal principal = securityContext.getUserPrincipal();
            if (principal != null && principal.getName() != null) {
                AuditContext.setCurrentUser(principal.getName());
                MDC.put("userId", principal.getName());
                return;
            }
        }
        AuditContext.setCurrentUser(AuditContext.SYSTEM_USER);
    }

    private boolean isPublicPath(String path) {
        if (path == null) {
            return false;
        }
        for (String publicPath : publicPaths) {
            if (path.startsWith(publicPath.trim())) {
                return true;
            }
        }
        return false;
    }
}
