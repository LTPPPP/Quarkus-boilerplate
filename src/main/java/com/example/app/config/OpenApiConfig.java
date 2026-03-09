package com.example.app.config;

import jakarta.ws.rs.core.Application;

import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Contact;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.info.License;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.servers.Server;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@OpenAPIDefinition(
        info = @Info(
                title = "Quarkus Enterprise Boilerplate API",
                version = "1.0.0",
                description = "Enterprise microservices boilerplate with Kafka, Multi-Tenancy, and gRPC support",
                contact = @Contact(
                        name = "Your Team",
                        email = "team@your-domain.example.com"
                ),
                license = @License(
                        name = "Apache 2.0",
                        url = "https://www.apache.org/licenses/LICENSE-2.0"
                )
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Local Development"),
                @Server(url = "https://api.your-domain.example.com", description = "Production")
        },
        security = @SecurityRequirement(name = "bearerAuth"),
        tags = {
                @Tag(name = "Users", description = "User management endpoints"),
                @Tag(name = "Orders", description = "Order management endpoints"),
                @Tag(name = "Payments", description = "Payment management endpoints"),
                @Tag(name = "Tenants", description = "Tenant administration endpoints"),
                @Tag(name = "DLQ", description = "Dead letter queue administration"),
                @Tag(name = "gRPC Gateway", description = "REST-to-gRPC bridge endpoints")
        }
)
@SecurityScheme(
        securitySchemeName = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig extends Application {
}
