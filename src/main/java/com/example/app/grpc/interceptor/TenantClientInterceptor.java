package com.example.app.grpc.interceptor;

import com.example.app.tenant.context.TenantContext;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

@ApplicationScoped
public class TenantClientInterceptor implements ClientInterceptor {

    private static final Logger LOG = Logger.getLogger(TenantClientInterceptor.class);

    public static final Metadata.Key<String> TENANT_ID_KEY =
            Metadata.Key.of("x-tenant-id", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                String tenantId = TenantContext.getCurrentTenant();
                headers.put(TENANT_ID_KEY, tenantId);
                LOG.debugf("Propagating tenant ID to outgoing gRPC call: method=%s, tenant=%s",
                        method.getFullMethodName(), tenantId);
                super.start(responseListener, headers);
            }
        };
    }
}
