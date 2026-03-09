package com.example.app.resource;

import com.example.app.domain.PaymentEntity;
import com.example.app.service.PaymentService;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;
import java.util.UUID;

@Path("/api/v1/payments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PaymentResource {

    @Inject
    PaymentService paymentService;

    @POST
    public Response initiatePayment(InitiatePaymentDto dto) {
        PaymentEntity payment = paymentService.initiatePayment(
                dto.orderId, dto.amount, dto.currency, dto.method);
        return Response.created(URI.create("/api/v1/payments/" + payment.getId()))
                .entity(payment).build();
    }

    @GET
    @Path("/{id}")
    public Response getPayment(@PathParam("id") UUID id) {
        PaymentEntity payment = paymentService.findById(id);
        return Response.ok(payment).build();
    }

    @GET
    @Path("/order/{orderId}")
    public Response getPaymentByOrderId(@PathParam("orderId") UUID orderId) {
        PaymentEntity payment = paymentService.findByOrderId(orderId);
        return Response.ok(payment).build();
    }

    @POST
    @Path("/{id}/refund")
    public Response processRefund(@PathParam("id") UUID id, RefundDto dto) {
        PaymentEntity payment = paymentService.processRefund(id, dto.amount, dto.reason);
        return Response.ok(Map.of(
                "refundId", "RF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                "paymentId", payment.getId().toString(),
                "status", "PROCESSED"
        )).build();
    }

    public static class InitiatePaymentDto {
        public UUID orderId;
        public BigDecimal amount;
        public String currency;
        public String method;
    }

    public static class RefundDto {
        public BigDecimal amount;
        public String reason;
    }
}
