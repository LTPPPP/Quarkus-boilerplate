package com.example.app.tenant.resolver;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;

import org.jboss.logging.Logger;

import java.util.Base64;
import java.util.Optional;

@ApplicationScoped
public class JwtClaimTenantResolver implements TenantResolver {

    private static final Logger LOG = Logger.getLogger(JwtClaimTenantResolver.class);

    @Override
    public Optional<String> resolve(ContainerRequestContext ctx) {
        String authHeader = ctx.getHeaderString("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }

        try {
            String token = authHeader.substring(7);
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return Optional.empty();
            }

            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));

            String tenantId = extractClaim(payloadJson, "tenant_id");
            if (tenantId != null && !tenantId.isBlank()) {
                return Optional.of(tenantId.trim().toLowerCase());
            }
        } catch (Exception e) {
            LOG.debugf("Could not extract tenant_id from JWT: %s", e.getMessage());
        }

        return Optional.empty();
    }

    @Override
    public int priority() {
        return 10;
    }

    private String extractClaim(String json, String claim) {
        String searchKey = "\"" + claim + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) {
            return null;
        }

        int colonIndex = json.indexOf(':', keyIndex + searchKey.length());
        if (colonIndex == -1) {
            return null;
        }

        int valueStart = colonIndex + 1;
        while (valueStart < json.length() && json.charAt(valueStart) == ' ') {
            valueStart++;
        }

        if (valueStart >= json.length()) {
            return null;
        }

        if (json.charAt(valueStart) == '"') {
            int valueEnd = json.indexOf('"', valueStart + 1);
            if (valueEnd == -1) {
                return null;
            }
            return json.substring(valueStart + 1, valueEnd);
        }

        int valueEnd = valueStart;
        while (valueEnd < json.length() && json.charAt(valueEnd) != ',' && json.charAt(valueEnd) != '}') {
            valueEnd++;
        }
        return json.substring(valueStart, valueEnd).trim();
    }
}
