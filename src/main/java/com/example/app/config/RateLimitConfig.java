package com.example.app.config;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

/**
 * Configuration for Redis-backed rate limiting.
 */
@ApplicationScoped
public class RateLimitConfig {

    @ConfigProperty(name = "app.rate-limit.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "app.rate-limit.requests-per-second", defaultValue = "100")
    int requestsPerSecond;

    @ConfigProperty(name = "app.rate-limit.burst-size", defaultValue = "200")
    int burstSize;

    @ConfigProperty(name = "app.rate-limit.per-tenant", defaultValue = "true")
    boolean perTenant;

    @ConfigProperty(name = "app.rate-limit.window-seconds", defaultValue = "60")
    int windowSeconds;

    @ConfigProperty(name = "app.rate-limit.excluded-paths")
    Optional<String> excludedPaths;

    public boolean isEnabled() {
        return enabled;
    }

    public int getRequestsPerSecond() {
        return requestsPerSecond;
    }

    public int getBurstSize() {
        return burstSize;
    }

    public boolean isPerTenant() {
        return perTenant;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public Optional<String> getExcludedPaths() {
        return excludedPaths;
    }

    /**
     * Max requests allowed per window = requestsPerSecond * windowSeconds.
     */
    public int getMaxRequestsPerWindow() {
        return requestsPerSecond * windowSeconds;
    }
}
