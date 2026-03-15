package com.example.app.config;

import com.example.app.tenant.context.TenantContext;

import io.quarkus.redis.datasource.RedisDataSource;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * JAX-RS request filter implementing Redis-backed sliding window rate limiting.
 * <p>
 * Uses a fixed-window counter approach with Redis INCR + EXPIRE for simplicity and atomicity.
 * Rate limits can be applied per-tenant or globally per source IP.
 * <p>
 * Response headers:
 * <ul>
 *   <li>X-RateLimit-Limit - maximum requests per window</li>
 *   <li>X-RateLimit-Remaining - remaining requests in current window</li>
 *   <li>X-RateLimit-Reset - epoch seconds when the current window resets</li>
 *   <li>Retry-After - seconds until the client can retry (only on 429)</li>
 * </ul>
 */
@Provider
@Priority(Priorities.USER - 100)
public class RateLimitFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(RateLimitFilter.class);

    /**
     * Lua script for atomic increment-and-expire:
     * - INCR the key
     * - If the key is new (count == 1), set expiry
     * - Return the current count
     */
    private static final String RATE_LIMIT_SCRIPT =
            "local count = redis.call('INCR', KEYS[1]) " +
                    "if tonumber(count) == 1 then " +
                    "  redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
                    "end " +
                    "return count";

    @Inject
    RateLimitConfig config;

    @Inject
    RedisDataSource redisDataSource;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!config.isEnabled()) {
            return;
        }

        String path = requestContext.getUriInfo().getPath();
        if (isExcluded(path)) {
            return;
        }

        String rateLimitKey = buildKey(requestContext);
        int windowSeconds = config.getWindowSeconds();
        int maxRequests = config.getMaxRequestsPerWindow();

        try {
            Object result = redisDataSource.execute(
                    "eval", RATE_LIMIT_SCRIPT, "1", rateLimitKey, String.valueOf(windowSeconds));

            long currentCount = result != null ? Long.parseLong(result.toString()) : 0;
            long remaining = Math.max(0, maxRequests - currentCount);

            // Calculate window reset time
            long windowStart = Instant.now().getEpochSecond() / windowSeconds * windowSeconds;
            long resetEpoch = windowStart + windowSeconds;

            // Always add rate limit headers to the response via request property
            requestContext.setProperty("X-RateLimit-Limit", String.valueOf(maxRequests));
            requestContext.setProperty("X-RateLimit-Remaining", String.valueOf(remaining));
            requestContext.setProperty("X-RateLimit-Reset", String.valueOf(resetEpoch));

            if (currentCount > maxRequests) {
                long retryAfter = resetEpoch - Instant.now().getEpochSecond();
                LOG.warnf("Rate limit exceeded: key=%s, count=%d, limit=%d", rateLimitKey, currentCount, maxRequests);

                requestContext.abortWith(Response.status(429)
                        .header("X-RateLimit-Limit", maxRequests)
                        .header("X-RateLimit-Remaining", 0)
                        .header("X-RateLimit-Reset", resetEpoch)
                        .header("Retry-After", Math.max(1, retryAfter))
                        .entity("{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Try again in "
                                + retryAfter + " seconds.\"}")
                        .type("application/json")
                        .build());
            }
        } catch (Exception e) {
            // If Redis is down, allow the request to pass (fail-open strategy)
            LOG.errorf(e, "Rate limiting failed (Redis error), allowing request: key=%s", rateLimitKey);
        }
    }

    private String buildKey(ContainerRequestContext requestContext) {
        String window = String.valueOf(Instant.now().getEpochSecond() / config.getWindowSeconds());

        if (config.isPerTenant()) {
            String tenantId = TenantContext.getCurrentTenant();
            if (tenantId != null && !tenantId.isBlank()) {
                return "ratelimit:" + tenantId + ":" + window;
            }
        }

        // Fallback to IP-based rate limiting
        String ip = requestContext.getHeaderString("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = requestContext.getHeaderString("X-Real-IP");
        }
        if (ip == null || ip.isBlank()) {
            ip = "unknown";
        } else {
            // Take only the first IP in case of proxy chain
            ip = ip.split(",")[0].trim();
        }
        return "ratelimit:" + ip + ":" + window;
    }

    private boolean isExcluded(String path) {
        if (path == null) return false;
        // Always exclude health/metrics endpoints
        if (path.startsWith("/q/health") || path.startsWith("/q/metrics") || path.startsWith("/q/openapi")) {
            return true;
        }
        return config.getExcludedPaths()
                .map(paths -> {
                    List<String> excludedList = Arrays.asList(paths.split(","));
                    return excludedList.stream().anyMatch(p -> path.startsWith(p.trim()));
                })
                .orElse(false);
    }
}
