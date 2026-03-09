package com.example.app.grpc.mapper;

import com.example.app.domain.PaymentEntity;
import com.example.app.grpc.proto.InitiatePaymentRequest;
import com.example.app.grpc.proto.PaymentProto;
import com.example.app.grpc.proto.Timestamp;

import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class PaymentGrpcMapper {

    public PaymentProto toProto(PaymentEntity entity) {
        PaymentProto.Builder builder = PaymentProto.newBuilder()
                .setId(entity.getId().toString())
                .setOrderId(entity.getOrderId().toString())
                .setAmount(entity.getAmount().doubleValue())
                .setCurrency(entity.getCurrency())
                .setStatus(entity.getStatus().name());

        if (entity.getGatewayRef() != null) {
            builder.setGatewayRef(entity.getGatewayRef());
        }

        return builder.build();
    }

    public PaymentEntity toEntity(InitiatePaymentRequest request) {
        PaymentEntity entity = new PaymentEntity();
        entity.setOrderId(UUID.fromString(request.getOrderId()));
        entity.setAmount(BigDecimal.valueOf(request.getAmount()));
        entity.setCurrency(request.getCurrency().isEmpty() ? "USD" : request.getCurrency());
        entity.setMethod(request.getMethod());
        entity.setStatus(PaymentEntity.PaymentStatus.INITIATED);
        return entity;
    }

    public Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    public Instant toInstant(Timestamp ts) {
        return Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos());
    }
}
