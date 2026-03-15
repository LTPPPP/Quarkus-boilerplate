package com.example.app.config;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * Response filter that adds rate limit headers from request properties
 * set by {@link RateLimitFilter}.
 */
@Provider
@Priority(Priorities.HEADER_DECORATOR)
public class RateLimitResponseFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        addHeaderIfPresent(requestContext, responseContext, "X-RateLimit-Limit");
        addHeaderIfPresent(requestContext, responseContext, "X-RateLimit-Remaining");
        addHeaderIfPresent(requestContext, responseContext, "X-RateLimit-Reset");
    }

    private void addHeaderIfPresent(ContainerRequestContext request,
                                     ContainerResponseContext response,
                                     String headerName) {
        Object value = request.getProperty(headerName);
        if (value != null) {
            response.getHeaders().putSingle(headerName, value);
        }
    }
}
