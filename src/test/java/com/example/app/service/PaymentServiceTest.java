package com.example.app.service;

import com.example.app.domain.PaymentEntity;
import com.example.app.exception.NotFoundException;
import com.example.app.exception.ValidationException;
import com.example.app.repository.PaymentRepository;
import com.example.app.tenant.context.TenantContext;

import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class PaymentServiceTest {

    private static final String TEST_TENANT = "payment-test-tenant";

    @Inject
    PaymentService paymentService;

    @Inject
    PaymentRepository paymentRepository;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(TEST_TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @Transactional
    void testInitiatePayment() {
        UUID orderId = UUID.randomUUID();
        PaymentEntity payment = paymentService.initiatePayment(
                orderId, new BigDecimal("99.99"), "USD", "CREDIT_CARD");

        assertNotNull(payment, "Payment should not be null");
        assertNotNull(payment.getId(), "Payment ID should be generated");
        assertEquals(orderId, payment.getOrderId());
        assertEquals(0, new BigDecimal("99.99").compareTo(payment.getAmount()));
        assertEquals("USD", payment.getCurrency());
        assertEquals("CREDIT_CARD", payment.getMethod());
        assertEquals(PaymentEntity.PaymentStatus.INITIATED, payment.getStatus());
        assertEquals(TEST_TENANT, payment.getTenantId());
        assertNotNull(payment.getGatewayRef(), "Gateway ref should be generated");
        assertTrue(payment.getGatewayRef().startsWith("GW-"), "Gateway ref should start with GW-");
    }

    @Test
    void testInitiatePaymentWithNullOrderId() {
        assertThrows(ValidationException.class,
                () -> paymentService.initiatePayment(null, BigDecimal.TEN, "USD", "CARD"));
    }

    @Test
    void testInitiatePaymentWithZeroAmount() {
        assertThrows(ValidationException.class,
                () -> paymentService.initiatePayment(UUID.randomUUID(), BigDecimal.ZERO, "USD", "CARD"));
    }

    @Test
    void testInitiatePaymentWithNegativeAmount() {
        assertThrows(ValidationException.class,
                () -> paymentService.initiatePayment(UUID.randomUUID(), new BigDecimal("-10"), "USD", "CARD"));
    }

    @Test
    @Transactional
    void testInitiatePaymentDefaultCurrency() {
        PaymentEntity payment = paymentService.initiatePayment(
                UUID.randomUUID(), BigDecimal.TEN, null, "CARD");
        assertEquals("USD", payment.getCurrency(), "Default currency should be USD");
    }

    @Test
    @Transactional
    void testInitiatePaymentBlankCurrency() {
        PaymentEntity payment = paymentService.initiatePayment(
                UUID.randomUUID(), BigDecimal.TEN, "  ", "CARD");
        assertEquals("USD", payment.getCurrency(), "Blank currency should default to USD");
    }

    @Test
    @Transactional
    void testFindPaymentById() {
        PaymentEntity created = paymentService.initiatePayment(
                UUID.randomUUID(), new BigDecimal("50.00"), "EUR", "WIRE");
        PaymentEntity found = paymentService.findById(created.getId());

        assertNotNull(found);
        assertEquals(created.getId(), found.getId());
        assertEquals("EUR", found.getCurrency());
    }

    @Test
    void testFindPaymentByIdNotFound() {
        assertThrows(NotFoundException.class,
                () -> paymentService.findById(UUID.randomUUID()));
    }

    @Test
    @Transactional
    void testFindPaymentByOrderId() {
        UUID orderId = UUID.randomUUID();
        paymentService.initiatePayment(orderId, new BigDecimal("25.00"), "USD", "PAYPAL");

        PaymentEntity found = paymentService.findByOrderId(orderId);
        assertNotNull(found);
        assertEquals(orderId, found.getOrderId());
    }

    @Test
    void testFindPaymentByOrderIdNotFound() {
        assertThrows(NotFoundException.class,
                () -> paymentService.findByOrderId(UUID.randomUUID()));
    }

    @Test
    @Transactional
    void testRefundSuccessfulPayment() {
        UUID orderId = UUID.randomUUID();
        PaymentEntity payment = paymentService.initiatePayment(
                orderId, new BigDecimal("100.00"), "USD", "CARD");

        payment.setStatus(PaymentEntity.PaymentStatus.SUCCESS);
        paymentRepository.persist(payment);

        PaymentEntity refunded = paymentService.processRefund(
                payment.getId(), new BigDecimal("50.00"), "Customer request");

        assertNotNull(refunded);
        assertEquals(PaymentEntity.PaymentStatus.REFUNDED, refunded.getStatus());
    }

    @Test
    @Transactional
    void testRefundNonSuccessfulPaymentThrows() {
        UUID orderId = UUID.randomUUID();
        PaymentEntity payment = paymentService.initiatePayment(
                orderId, new BigDecimal("100.00"), "USD", "CARD");

        assertThrows(ValidationException.class,
                () -> paymentService.processRefund(payment.getId(), new BigDecimal("50.00"), "Test"));
    }

    @Test
    @Transactional
    void testRefundExceedsAmountThrows() {
        UUID orderId = UUID.randomUUID();
        PaymentEntity payment = paymentService.initiatePayment(
                orderId, new BigDecimal("100.00"), "USD", "CARD");

        payment.setStatus(PaymentEntity.PaymentStatus.SUCCESS);
        paymentRepository.persist(payment);

        assertThrows(ValidationException.class,
                () -> paymentService.processRefund(payment.getId(), new BigDecimal("150.00"), "Too much"));
    }
}
