package com.example.app.service;

import com.example.app.domain.PaymentEntity;
import com.example.app.event.producer.KafkaEventProducer;
import com.example.app.exception.NotFoundException;
import com.example.app.exception.ValidationException;
import com.example.app.repository.PaymentRepository;
import com.example.app.tenant.context.TenantContext;
import com.example.app.util.TraceContextPropagator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService Unit Tests")
class PaymentServiceUnitTest {

    private static final String TEST_TENANT = "test-tenant";
    private static final String TEST_TRACE_ID = "trace-abc-123";

    @Mock
    PaymentRepository paymentRepository;

    @Mock
    KafkaEventProducer kafkaEventProducer;

    @Mock
    TraceContextPropagator traceContextPropagator;

    @InjectMocks
    PaymentService paymentService;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(TEST_TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ---- findById ----

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("should return payment when found")
        void findById_found() {
            UUID id = UUID.randomUUID();
            PaymentEntity payment = createPayment(id, PaymentEntity.PaymentStatus.SUCCESS);
            when(paymentRepository.findByIdOptional(id)).thenReturn(Optional.of(payment));

            PaymentEntity result = paymentService.findById(id);

            assertNotNull(result);
            assertEquals(id, result.getId());
            verify(paymentRepository).findByIdOptional(id);
        }

        @Test
        @DisplayName("should throw NotFoundException when not found")
        void findById_notFound() {
            UUID id = UUID.randomUUID();
            when(paymentRepository.findByIdOptional(id)).thenReturn(Optional.empty());

            NotFoundException ex = assertThrows(NotFoundException.class,
                    () -> paymentService.findById(id));
            assertEquals("Payment", ex.getResourceType());
        }
    }

    // ---- findByOrderId ----

    @Nested
    @DisplayName("findByOrderId")
    class FindByOrderId {

        @Test
        @DisplayName("should return payment when order has payment")
        void findByOrderId_found() {
            UUID orderId = UUID.randomUUID();
            PaymentEntity payment = createPayment(UUID.randomUUID(), PaymentEntity.PaymentStatus.INITIATED);
            payment.setOrderId(orderId);
            when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));

            PaymentEntity result = paymentService.findByOrderId(orderId);

            assertNotNull(result);
            assertEquals(orderId, result.getOrderId());
        }

        @Test
        @DisplayName("should throw NotFoundException when no payment for order")
        void findByOrderId_notFound() {
            UUID orderId = UUID.randomUUID();
            when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

            NotFoundException ex = assertThrows(NotFoundException.class,
                    () -> paymentService.findByOrderId(orderId));
            assertTrue(ex.getMessage().contains("orderId="));
        }
    }

    // ---- initiatePayment ----

    @Nested
    @DisplayName("initiatePayment")
    class InitiatePayment {

        @Test
        @DisplayName("should initiate payment with valid input")
        void initiatePayment_success() {
            UUID orderId = UUID.randomUUID();
            BigDecimal amount = new BigDecimal("99.99");
            when(traceContextPropagator.getCurrentTraceId()).thenReturn(TEST_TRACE_ID);

            PaymentEntity result = paymentService.initiatePayment(orderId, amount, "EUR", "CARD");

            assertNotNull(result);
            assertEquals(orderId, result.getOrderId());
            assertEquals(amount, result.getAmount());
            assertEquals("EUR", result.getCurrency());
            assertEquals("CARD", result.getMethod());
            assertEquals(PaymentEntity.PaymentStatus.INITIATED, result.getStatus());
            assertEquals(TEST_TENANT, result.getTenantId());
            assertNotNull(result.getGatewayRef());
            assertTrue(result.getGatewayRef().startsWith("GW-"));

            verify(paymentRepository).persist(any(PaymentEntity.class));
            verify(kafkaEventProducer).publish(eq("payment-events-out"), any());
        }

        @Test
        @DisplayName("should default currency to USD when null")
        void initiatePayment_nullCurrency() {
            UUID orderId = UUID.randomUUID();
            when(traceContextPropagator.getCurrentTraceId()).thenReturn(TEST_TRACE_ID);

            PaymentEntity result = paymentService.initiatePayment(orderId, BigDecimal.TEN, null, "CARD");

            assertEquals("USD", result.getCurrency());
        }

        @Test
        @DisplayName("should default currency to USD when blank")
        void initiatePayment_blankCurrency() {
            UUID orderId = UUID.randomUUID();
            when(traceContextPropagator.getCurrentTraceId()).thenReturn(TEST_TRACE_ID);

            PaymentEntity result = paymentService.initiatePayment(orderId, BigDecimal.TEN, "  ", "CARD");

            assertEquals("USD", result.getCurrency());
        }

        @Test
        @DisplayName("should throw ValidationException when orderId is null")
        void initiatePayment_nullOrderId() {
            assertThrows(ValidationException.class,
                    () -> paymentService.initiatePayment(null, BigDecimal.TEN, "USD", "CARD"));
            verify(paymentRepository, never()).persist(any(PaymentEntity.class));
        }

        @Test
        @DisplayName("should throw ValidationException when amount is null")
        void initiatePayment_nullAmount() {
            assertThrows(ValidationException.class,
                    () -> paymentService.initiatePayment(UUID.randomUUID(), null, "USD", "CARD"));
            verify(paymentRepository, never()).persist(any(PaymentEntity.class));
        }

        @Test
        @DisplayName("should throw ValidationException when amount is zero")
        void initiatePayment_zeroAmount() {
            assertThrows(ValidationException.class,
                    () -> paymentService.initiatePayment(UUID.randomUUID(), BigDecimal.ZERO, "USD", "CARD"));
            verify(paymentRepository, never()).persist(any(PaymentEntity.class));
        }

        @Test
        @DisplayName("should throw ValidationException when amount is negative")
        void initiatePayment_negativeAmount() {
            assertThrows(ValidationException.class,
                    () -> paymentService.initiatePayment(UUID.randomUUID(), new BigDecimal("-5.00"), "USD", "CARD"));
            verify(paymentRepository, never()).persist(any(PaymentEntity.class));
        }

        @Test
        @DisplayName("should still succeed even if event publishing fails")
        void initiatePayment_eventPublishingFails() {
            UUID orderId = UUID.randomUUID();
            when(traceContextPropagator.getCurrentTraceId()).thenReturn(TEST_TRACE_ID);
            doThrow(new RuntimeException("Kafka down"))
                    .when(kafkaEventProducer).publish(anyString(), any());

            PaymentEntity result = paymentService.initiatePayment(orderId, BigDecimal.TEN, "USD", "CARD");

            assertNotNull(result);
            verify(paymentRepository).persist(any(PaymentEntity.class));
        }

        @Test
        @DisplayName("should generate unique gateway reference")
        void initiatePayment_uniqueGatewayRef() {
            UUID orderId1 = UUID.randomUUID();
            UUID orderId2 = UUID.randomUUID();
            when(traceContextPropagator.getCurrentTraceId()).thenReturn(TEST_TRACE_ID);

            PaymentEntity result1 = paymentService.initiatePayment(orderId1, BigDecimal.TEN, "USD", "CARD");
            PaymentEntity result2 = paymentService.initiatePayment(orderId2, BigDecimal.TEN, "USD", "CARD");

            assertNotEquals(result1.getGatewayRef(), result2.getGatewayRef());
        }

        @Test
        @DisplayName("should use current tenant from TenantContext")
        void initiatePayment_usesTenantContext() {
            String customTenant = "custom-tenant";
            TenantContext.setCurrentTenant(customTenant);
            UUID orderId = UUID.randomUUID();
            when(traceContextPropagator.getCurrentTraceId()).thenReturn(TEST_TRACE_ID);

            PaymentEntity result = paymentService.initiatePayment(orderId, BigDecimal.TEN, "USD", "CARD");

            assertEquals(customTenant, result.getTenantId());
        }
    }

    // ---- processRefund ----

    @Nested
    @DisplayName("processRefund")
    class ProcessRefund {

        @Test
        @DisplayName("should refund successful payment")
        void processRefund_success() {
            UUID paymentId = UUID.randomUUID();
            PaymentEntity payment = createPayment(paymentId, PaymentEntity.PaymentStatus.SUCCESS);
            payment.setAmount(new BigDecimal("100.00"));
            when(paymentRepository.findByIdOptional(paymentId)).thenReturn(Optional.of(payment));
            when(traceContextPropagator.getCurrentTraceId()).thenReturn(TEST_TRACE_ID);

            PaymentEntity result = paymentService.processRefund(paymentId, new BigDecimal("50.00"), "Customer request");

            assertEquals(PaymentEntity.PaymentStatus.REFUNDED, result.getStatus());
            verify(paymentRepository).persist(payment);
            verify(kafkaEventProducer).publish(eq("payment-events-out"), any());
        }

        @Test
        @DisplayName("should allow full refund equal to payment amount")
        void processRefund_fullRefund() {
            UUID paymentId = UUID.randomUUID();
            PaymentEntity payment = createPayment(paymentId, PaymentEntity.PaymentStatus.SUCCESS);
            payment.setAmount(new BigDecimal("100.00"));
            when(paymentRepository.findByIdOptional(paymentId)).thenReturn(Optional.of(payment));
            when(traceContextPropagator.getCurrentTraceId()).thenReturn(TEST_TRACE_ID);

            PaymentEntity result = paymentService.processRefund(paymentId, new BigDecimal("100.00"), "Full refund");

            assertEquals(PaymentEntity.PaymentStatus.REFUNDED, result.getStatus());
        }

        @Test
        @DisplayName("should throw ValidationException when payment is not SUCCESS")
        void processRefund_notSuccessful() {
            UUID paymentId = UUID.randomUUID();
            PaymentEntity payment = createPayment(paymentId, PaymentEntity.PaymentStatus.INITIATED);
            when(paymentRepository.findByIdOptional(paymentId)).thenReturn(Optional.of(payment));

            ValidationException ex = assertThrows(ValidationException.class,
                    () -> paymentService.processRefund(paymentId, BigDecimal.TEN, "reason"));
            assertTrue(ex.getMessage().contains("INITIATED"));
        }

        @Test
        @DisplayName("should throw ValidationException when payment is PENDING")
        void processRefund_pendingStatus() {
            UUID paymentId = UUID.randomUUID();
            PaymentEntity payment = createPayment(paymentId, PaymentEntity.PaymentStatus.PENDING);
            when(paymentRepository.findByIdOptional(paymentId)).thenReturn(Optional.of(payment));

            assertThrows(ValidationException.class,
                    () -> paymentService.processRefund(paymentId, BigDecimal.TEN, "reason"));
        }

        @Test
        @DisplayName("should throw ValidationException when payment is already REFUNDED")
        void processRefund_alreadyRefunded() {
            UUID paymentId = UUID.randomUUID();
            PaymentEntity payment = createPayment(paymentId, PaymentEntity.PaymentStatus.REFUNDED);
            when(paymentRepository.findByIdOptional(paymentId)).thenReturn(Optional.of(payment));

            assertThrows(ValidationException.class,
                    () -> paymentService.processRefund(paymentId, BigDecimal.TEN, "reason"));
        }

        @Test
        @DisplayName("should throw ValidationException when refund exceeds payment amount")
        void processRefund_exceedsAmount() {
            UUID paymentId = UUID.randomUUID();
            PaymentEntity payment = createPayment(paymentId, PaymentEntity.PaymentStatus.SUCCESS);
            payment.setAmount(new BigDecimal("50.00"));
            when(paymentRepository.findByIdOptional(paymentId)).thenReturn(Optional.of(payment));

            assertThrows(ValidationException.class,
                    () -> paymentService.processRefund(paymentId, new BigDecimal("100.00"), "Too much"));
        }

        @Test
        @DisplayName("should throw NotFoundException when payment not found")
        void processRefund_notFound() {
            UUID paymentId = UUID.randomUUID();
            when(paymentRepository.findByIdOptional(paymentId)).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class,
                    () -> paymentService.processRefund(paymentId, BigDecimal.TEN, "reason"));
        }

        @Test
        @DisplayName("should still succeed even if event publishing fails")
        void processRefund_eventPublishingFails() {
            UUID paymentId = UUID.randomUUID();
            PaymentEntity payment = createPayment(paymentId, PaymentEntity.PaymentStatus.SUCCESS);
            payment.setAmount(new BigDecimal("100.00"));
            when(paymentRepository.findByIdOptional(paymentId)).thenReturn(Optional.of(payment));
            when(traceContextPropagator.getCurrentTraceId()).thenReturn(TEST_TRACE_ID);
            doThrow(new RuntimeException("Kafka down"))
                    .when(kafkaEventProducer).publish(anyString(), any());

            PaymentEntity result = paymentService.processRefund(paymentId, new BigDecimal("50.00"), "reason");

            assertNotNull(result);
            assertEquals(PaymentEntity.PaymentStatus.REFUNDED, result.getStatus());
            verify(paymentRepository).persist(payment);
        }
    }

    // ---- helpers ----

    private PaymentEntity createPayment(UUID id, PaymentEntity.PaymentStatus status) {
        PaymentEntity payment = new PaymentEntity();
        payment.setId(id);
        payment.setOrderId(UUID.randomUUID());
        payment.setAmount(new BigDecimal("100.00"));
        payment.setCurrency("USD");
        payment.setMethod("CARD");
        payment.setStatus(status);
        payment.setTenantId(TEST_TENANT);
        payment.setGatewayRef("GW-TEST1234");
        return payment;
    }
}
