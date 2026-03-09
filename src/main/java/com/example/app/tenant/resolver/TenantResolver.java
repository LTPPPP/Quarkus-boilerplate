package com.example.app.tenant.resolver;

import jakarta.ws.rs.container.ContainerRequestContext;

import java.util.Optional;

public interface TenantResolver {

    Optional<String> resolve(ContainerRequestContext ctx);

    int priority();
}
