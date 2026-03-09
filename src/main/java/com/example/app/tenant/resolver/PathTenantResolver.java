package com.example.app.tenant.resolver;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class PathTenantResolver implements TenantResolver {

    private static final Pattern TENANT_PATH_PATTERN = Pattern.compile("^/api/v\\d+/([a-z0-9_-]+)/.*");

    @Override
    public Optional<String> resolve(ContainerRequestContext ctx) {
        String path = ctx.getUriInfo().getPath();
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }

        Matcher matcher = TENANT_PATH_PATTERN.matcher(path);
        if (matcher.matches()) {
            String candidate = matcher.group(1);
            if (!isReservedSegment(candidate)) {
                return Optional.of(candidate);
            }
        }

        return Optional.empty();
    }

    @Override
    public int priority() {
        return 40;
    }

    private boolean isReservedSegment(String segment) {
        return switch (segment) {
            case "admin", "auth", "public", "health", "grpc", "users", "orders", "payments" -> true;
            default -> false;
        };
    }
}
