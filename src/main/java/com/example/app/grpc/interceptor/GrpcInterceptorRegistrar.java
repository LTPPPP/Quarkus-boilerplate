package com.example.app.grpc.interceptor;

import io.grpc.ServerInterceptor;
import io.quarkus.grpc.GlobalInterceptor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@ApplicationScoped
public class GrpcInterceptorRegistrar {

    @Inject
    LoggingInterceptor loggingInterceptor;

    @Inject
    AuthInterceptor authInterceptor;

    @Inject
    TenantInterceptor tenantInterceptor;

    @Produces
    @Singleton
    @GlobalInterceptor
    public ServerInterceptor loggingServerInterceptor() {
        return loggingInterceptor;
    }

    @Produces
    @Singleton
    @GlobalInterceptor
    public ServerInterceptor authServerInterceptor() {
        return authInterceptor;
    }

    @Produces
    @Singleton
    @GlobalInterceptor
    public ServerInterceptor tenantServerInterceptor() {
        return tenantInterceptor;
    }
}
