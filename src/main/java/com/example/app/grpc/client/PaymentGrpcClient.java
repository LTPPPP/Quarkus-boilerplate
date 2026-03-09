package com.example.app.grpc.client;

import com.example.app.grpc.proto.InitiatePaymentRequest;
import com.example.app.grpc.proto.PaymentGrpcService;
import com.example.app.grpc.proto.PaymentProto;
import com.example.app.grpc.proto.PaymentStatusRequest;

import io.quarkus.grpc.GrpcClient;
import io.smallrye.mutiny.Uni;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import java.time.Duration;

@ApplicationScoped
public class PaymentGrpcClient {

    private static final Logger LOG = Logger.getLogger(PaymentGrpcClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Inject
    @GrpcClient("payment-service")
    PaymentGrpcService paymentGrpcService;

    public PaymentProto initiatePayment(String orderId, double amount, String currency) {
        try {
            InitiatePaymentRequest request = InitiatePaymentRequest.newBuilder()
                    .setOrderId(orderId)
                    .setAmount(amount)
                    .setCurrency(currency)
                    .setMethod("CARD")
                    .build();

            PaymentProto response = paymentGrpcService.initiatePayment(request)
                    .await().atMost(TIMEOUT);

            LOG.infof("Payment initiated via gRPC client: paymentId=%s, orderId=%s, amount=%s %s",
                    response.getId(), orderId, amount, currency);
            return response;
        } catch (Exception e) {
            LOG.errorf(e, "gRPC call failed: initiatePayment for orderId=%s", orderId);
            throw new RuntimeException("Payment initiation failed via gRPC: " + e.getMessage(), e);
        }
    }

    public PaymentProto getPaymentStatus(String paymentId) {
        try {
            PaymentStatusRequest request = PaymentStatusRequest.newBuilder()
                    .setPaymentId(paymentId)
                    .build();

            PaymentProto response = paymentGrpcService.getPaymentStatus(request)
                    .await().atMost(TIMEOUT);

            LOG.debugf("Payment status retrieved via gRPC client: paymentId=%s, status=%s",
                    paymentId, response.getStatus());
            return response;
        } catch (Exception e) {
            LOG.errorf(e, "gRPC call failed: getPaymentStatus for paymentId=%s", paymentId);
            throw new RuntimeException("Payment status check failed via gRPC: " + e.getMessage(), e);
        }
    }

    public Uni<PaymentProto> initiatePaymentAsync(String orderId, double amount, String currency) {
        InitiatePaymentRequest request = InitiatePaymentRequest.newBuilder()
                .setOrderId(orderId)
                .setAmount(amount)
                .setCurrency(currency)
                .setMethod("CARD")
                .build();

        return paymentGrpcService.initiatePayment(request)
                .ifNoItem().after(TIMEOUT).fail();
    }
}
