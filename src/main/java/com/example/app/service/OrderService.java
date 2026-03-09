package com.example.app.service;

import com.example.app.domain.OrderEntity;
import com.example.app.domain.OrderItemEntity;
import com.example.app.event.producer.OrderEventProducer;
import com.example.app.exception.NotFoundException;
import com.example.app.exception.ValidationException;
import com.example.app.repository.OrderRepository;
import com.example.app.tenant.context.TenantContext;

import io.quarkus.panache.common.Page;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class OrderService {

    private static final Logger LOG = Logger.getLogger(OrderService.class);

    @Inject
    OrderRepository orderRepository;

    @Inject
    OrderEventProducer orderEventProducer;

    public OrderEntity findById(UUID id) {
        return orderRepository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Order", id.toString()));
    }

    public List<OrderEntity> listByUserId(UUID userId, int page, int size) {
        if (size <= 0) size = 20;
        if (size > 100) size = 100;
        String tenantId = TenantContext.getCurrentTenant();
        return orderRepository.find("userId = ?1 and tenantId = ?2", userId, tenantId)
                .page(Page.of(page, size))
                .list();
    }

    public long countByUserId(UUID userId) {
        String tenantId = TenantContext.getCurrentTenant();
        return orderRepository.count("userId = ?1 and tenantId = ?2", userId, tenantId);
    }

    @Transactional
    public OrderEntity createOrder(UUID userId, List<OrderItemEntity> items) {
        if (userId == null) {
            throw new ValidationException("User ID is required");
        }
        if (items == null || items.isEmpty()) {
            throw new ValidationException("Order must contain at least one item");
        }

        String tenantId = TenantContext.getCurrentTenant();

        OrderEntity order = new OrderEntity();
        order.setUserId(userId);
        order.setTenantId(tenantId);
        order.setStatus(OrderEntity.OrderStatus.PLACED);

        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemEntity item : items) {
            order.addItem(item);
            total = total.add(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        order.setTotalAmount(total);

        orderRepository.persist(order);
        LOG.infof("Order created: id=%s, userId=%s, total=%s, tenant=%s", order.getId(), userId, total, tenantId);

        try {
            orderEventProducer.publishOrderPlaced(order, tenantId);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to publish ORDER_PLACED event for order %s", order.getId());
        }

        return order;
    }

    @Transactional
    public OrderEntity updateStatus(UUID orderId, OrderEntity.OrderStatus newStatus, String reason) {
        OrderEntity order = findById(orderId);
        String previousStatus = order.getStatus().name();
        String tenantId = TenantContext.getCurrentTenant();

        order.setStatus(newStatus);
        orderRepository.persist(order);

        LOG.infof("Order %s status changed: %s -> %s (reason: %s), tenant=%s",
                orderId, previousStatus, newStatus, reason, tenantId);

        try {
            orderEventProducer.publishOrderStatusChanged(order, previousStatus, tenantId);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to publish order status event for order %s", orderId);
        }

        return order;
    }
}
