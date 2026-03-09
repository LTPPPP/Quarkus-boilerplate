package com.example.app.grpc.interceptor;

import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

@ApplicationScoped
public class LoggingInterceptor implements ServerInterceptor {

    private static final Logger LOG = Logger.getLogger(LoggingInterceptor.class);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String methodName = call.getMethodDescriptor().getFullMethodName();
        long startTime = System.currentTimeMillis();

        String tenantId = headers.get(TenantInterceptor.TENANT_ID_KEY);
        MDC.put("grpc.method", methodName);
        if (tenantId != null) {
            MDC.put("tenantId", tenantId);
        }

        LOG.infof("gRPC call started: method=%s, tenant=%s", methodName, tenantId);

        ServerCall<ReqT, RespT> wrappedCall = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
            @Override
            public void close(Status status, Metadata trailers) {
                long duration = System.currentTimeMillis() - startTime;
                LOG.infof("gRPC call completed: method=%s, status=%s, duration=%dms, tenant=%s",
                        methodName, status.getCode(), duration, tenantId);
                MDC.remove("grpc.method");
                MDC.remove("tenantId");
                super.close(status, trailers);
            }
        };

        ServerCall.Listener<ReqT> listener = next.startCall(wrappedCall, headers);

        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(listener) {
            @Override
            public void onMessage(ReqT message) {
                LOG.debugf("gRPC request received: method=%s", methodName);
                super.onMessage(message);
            }

            @Override
            public void onCancel() {
                long duration = System.currentTimeMillis() - startTime;
                LOG.warnf("gRPC call cancelled: method=%s, duration=%dms", methodName, duration);
                super.onCancel();
            }
        };
    }
}
