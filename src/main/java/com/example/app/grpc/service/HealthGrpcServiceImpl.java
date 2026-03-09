package com.example.app.grpc.service;

import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.health.v1.HealthGrpc;

import io.quarkus.grpc.GrpcService;

import org.jboss.logging.Logger;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@GrpcService
public class HealthGrpcServiceImpl extends HealthGrpc.HealthImplBase {

    private static final Logger LOG = Logger.getLogger(HealthGrpcServiceImpl.class);
    private static final long WATCH_INTERVAL_SECONDS = 5;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "grpc-health-watch");
        t.setDaemon(true);
        return t;
    });

    @Override
    public void check(HealthCheckRequest request,
                      io.grpc.stub.StreamObserver<HealthCheckResponse> responseObserver) {
        String serviceName = request.getService();
        LOG.debugf("gRPC health check for service: %s", serviceName.isEmpty() ? "(all)" : serviceName);

        HealthCheckResponse response = HealthCheckResponse.newBuilder()
                .setStatus(ServingStatus.SERVING)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void watch(HealthCheckRequest request,
                      io.grpc.stub.StreamObserver<HealthCheckResponse> responseObserver) {
        String serviceName = request.getService();
        LOG.infof("gRPC health watch started for service: %s", serviceName.isEmpty() ? "(all)" : serviceName);

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                ServingStatus status = evaluateHealth(serviceName);
                HealthCheckResponse response = HealthCheckResponse.newBuilder()
                        .setStatus(status)
                        .build();
                responseObserver.onNext(response);
            } catch (Exception e) {
                LOG.warnf("Health watch stream error for service %s: %s", serviceName, e.getMessage());
                responseObserver.onError(io.grpc.Status.INTERNAL
                        .withDescription("Health watch failed: " + e.getMessage())
                        .asRuntimeException());
            }
        }, 0, WATCH_INTERVAL_SECONDS, TimeUnit.SECONDS);

        io.grpc.Context.current().addListener(context -> {
            future.cancel(false);
            LOG.infof("gRPC health watch cancelled for service: %s", serviceName.isEmpty() ? "(all)" : serviceName);
        }, Runnable::run);
    }

    private ServingStatus evaluateHealth(String serviceName) {
        try {
            Runtime runtime = Runtime.getRuntime();
            long freeMemory = runtime.freeMemory();
            long totalMemory = runtime.totalMemory();
            double memoryUsagePercent = ((double) (totalMemory - freeMemory) / totalMemory) * 100;

            if (memoryUsagePercent > 95) {
                LOG.warnf("Health degraded: memory usage at %.1f%%", memoryUsagePercent);
                return ServingStatus.NOT_SERVING;
            }

            return ServingStatus.SERVING;
        } catch (Exception e) {
            LOG.errorf(e, "Health evaluation failed for service: %s", serviceName);
            return ServingStatus.UNKNOWN;
        }
    }
}
