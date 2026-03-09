package com.example.app.grpc.interceptor;

import com.example.app.tenant.context.TenantContext;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

@ApplicationScoped
public class TenantInterceptor implements ServerInterceptor {

    private static final Logger LOG = Logger.getLogger(TenantInterceptor.class);

    public static final Metadata.Key<String> TENANT_ID_KEY =
            Metadata.Key.of("x-tenant-id", Metadata.ASCII_STRING_MARSHALLER);

    public static final Context.Key<String> TENANT_CTX = Context.key("tenantId");

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String tenantId = headers.get(TENANT_ID_KEY);

        if (tenantId == null || tenantId.isBlank()) {
            call.close(Status.INVALID_ARGUMENT.withDescription("Missing required header: x-tenant-id"), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        String normalizedTenantId = tenantId.trim().toLowerCase();
        if (!normalizedTenantId.matches("^[a-z0-9][a-z0-9_-]*$")) {
            call.close(Status.INVALID_ARGUMENT.withDescription("Invalid tenant ID format: " + tenantId), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        TenantContext.setCurrentTenant(normalizedTenantId);
        Context ctx = Context.current().withValue(TENANT_CTX, normalizedTenantId);

        LOG.debugf("gRPC tenant context set: %s for method: %s",
                normalizedTenantId, call.getMethodDescriptor().getFullMethodName());

        ServerCall<ReqT, RespT> wrappedCall = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
            @Override
            public void close(Status status, Metadata trailers) {
                TenantContext.clear();
                super.close(status, trailers);
            }
        };

        return Contexts.interceptCall(ctx, wrappedCall, headers, next);
    }
}
