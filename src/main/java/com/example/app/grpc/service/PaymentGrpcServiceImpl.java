package com.example.app.grpc.service;

import com.example.app.domain.PaymentEntity;
import com.example.app.exception.NotFoundException;
import com.example.app.exception.ValidationException;
import com.example.app.grpc.mapper.PaymentGrpcMapper;
import com.example.app.grpc.proto.InitiatePaymentRequest;
import com.example.app.grpc.proto.PaymentGrpcService;
import com.example.app.grpc.proto.PaymentProto;
import com.example.app.grpc.proto.PaymentStatusRequest;
import com.example.app.grpc.proto.RefundRequest;
import com.example.app.grpc.proto.RefundResponse;
import com.example.app.grpc.proto.Timestamp;
import com.example.app.service.PaymentService;

import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@GrpcService
public class PaymentGrpcServiceImpl implements PaymentGrpcService {

    private static final Logger LOG = Logger.getLogger(PaymentGrpcServiceImpl.class);

    @Inject
    PaymentService paymentService;

    @Inject
    PaymentGrpcMapper mapper;

    @Override
    public Uni<PaymentProto> initiatePayment(InitiatePaymentRequest request) {
        return Uni.createFrom().item(() -> {
            try {
                UUID orderId = UUID.fromString(request.getOrderId());
                PaymentEntity entity = paymentService.initiatePayment(
                        orderId,
                        BigDecimal.valueOf(request.getAmount()),
                        request.getCurrency(),
                        request.getMethod()
                );

                LOG.infof("Payment initiated via gRPC: %s for order %s", entity.getId(), request.getOrderId());
                return mapper.toProto(entity);
            } catch (ValidationException e) {
                throw Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException();
            } catch (Exception e) {
                LOG.error("Error in initiatePayment", e);
                throw Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException();
            }
        });
    }

    @Override
    public Uni<PaymentProto> getPaymentStatus(PaymentStatusRequest request) {
        return Uni.createFrom().item(() -> {
            try {
                UUID paymentId = UUID.fromString(request.getPaymentId());
                PaymentEntity payment = paymentService.findById(paymentId);
                return mapper.toProto(payment);
            } catch (NotFoundException e) {
                throw Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException();
            } catch (IllegalArgumentException e) {
                throw Status.INVALID_ARGUMENT.withDescription("Invalid payment ID format").asRuntimeException();
            } catch (Exception e) {
                LOG.error("Error in getPaymentStatus", e);
                throw Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException();
            }
        });
    }

    @Override
    public Uni<RefundResponse> processRefund(RefundRequest request) {
        return Uni.createFrom().item(() -> {
            try {
                UUID paymentId = UUID.fromString(request.getPaymentId());
                BigDecimal refundAmount = BigDecimal.valueOf(request.getAmount());

                paymentService.processRefund(paymentId, refundAmount, request.getReason());

                Instant now = Instant.now();
                return RefundResponse.newBuilder()
                        .setRefundId("RF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                        .setStatus("PROCESSED")
                        .setProcessedAt(Timestamp.newBuilder()
                                .setSeconds(now.getEpochSecond())
                                .setNanos(now.getNano())
                                .build())
                        .build();
            } catch (NotFoundException e) {
                throw Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException();
            } catch (ValidationException e) {
                throw Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException();
            } catch (Exception e) {
                LOG.error("Error in processRefund", e);
                throw Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException();
            }
        });
    }

    @Override
    public Multi<PaymentProto> streamPaymentEvents(PaymentStatusRequest request) {
        return Multi.createFrom().emitter(emitter -> {
            try {
                UUID paymentId = UUID.fromString(request.getPaymentId());
                PaymentEntity payment = paymentService.findById(paymentId);
                emitter.emit(mapper.toProto(payment));
                emitter.complete();
            } catch (NotFoundException e) {
                emitter.fail(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
            } catch (Exception e) {
                emitter.fail(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
            }
        });
    }
}
