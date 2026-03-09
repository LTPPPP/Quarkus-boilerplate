package com.example.app.tenant.resolver;

import com.example.app.exception.UnauthorizedException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;

import org.jboss.logging.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class TenantResolverChain {

    private static final Logger LOG = Logger.getLogger(TenantResolverChain.class);

    private final List<TenantResolver> resolvers;

    @Inject
    public TenantResolverChain(Instance<TenantResolver> resolverInstances) {
        this.resolvers = resolverInstances.stream()
                .sorted(Comparator.comparingInt(TenantResolver::priority))
                .collect(Collectors.toList());
        LOG.infof("Initialized tenant resolver chain with %d resolvers: %s",
                resolvers.size(),
                resolvers.stream().map(r -> r.getClass().getSimpleName()).collect(Collectors.joining(", ")));
    }

    public String resolve(ContainerRequestContext ctx) {
        for (TenantResolver resolver : resolvers) {
            Optional<String> tenantId = resolver.resolve(ctx);
            if (tenantId.isPresent()) {
                LOG.debugf("Tenant resolved by %s: %s", resolver.getClass().getSimpleName(), tenantId.get());
                return tenantId.get();
            }
        }
        throw new UnauthorizedException("Could not resolve tenant from request. " +
                "Provide tenant via JWT claim, X-Tenant-ID header, subdomain, or URL path.");
    }
}
