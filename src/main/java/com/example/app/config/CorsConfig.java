package com.example.app.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@Provider
@ApplicationScoped
public class CorsConfig implements ContainerResponseFilter {

    @ConfigProperty(name = "app.cors.allowed-origins", defaultValue = "*")
    String allowedOrigins;

    @ConfigProperty(name = "app.cors.allowed-methods", defaultValue = "GET,POST,PUT,DELETE,PATCH,OPTIONS")
    String allowedMethods;

    @ConfigProperty(name = "app.cors.allowed-headers", defaultValue = "Content-Type,Authorization,X-Tenant-ID,X-Request-ID")
    String allowedHeaders;

    @ConfigProperty(name = "app.cors.max-age", defaultValue = "86400")
    int maxAge;

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        responseContext.getHeaders().add("Access-Control-Allow-Origin", allowedOrigins);
        responseContext.getHeaders().add("Access-Control-Allow-Methods", allowedMethods);
        responseContext.getHeaders().add("Access-Control-Allow-Headers", allowedHeaders);
        responseContext.getHeaders().add("Access-Control-Max-Age", String.valueOf(maxAge));
        responseContext.getHeaders().add("Access-Control-Allow-Credentials", "true");
    }
}
