package com.example.app.grpc.service;

import com.example.app.domain.OrderEntity;
import com.example.app.domain.OrderItemEntity;
import com.example.app.exception.NotFoundException;
import com.example.app.exception.ValidationException;
import com.example.app.grpc.mapper.OrderGrpcMapper;
import com.example.app.grpc.proto.CreateOrderRequest;
import com.example.app.grpc.proto.GetOrderRequest;
import com.example.app.grpc.proto.ListOrdersByUserRequest;
import com.example.app.grpc.proto.ListOrdersResponse;
import com.example.app.grpc.proto.OrderGrpcService;
import com.example.app.grpc.proto.OrderItemProto;
import com.example.app.grpc.proto.OrderProto;
import com.example.app.grpc.proto.UpdateOrderStatusRequest;
import com.example.app.service.OrderService;
import com.example.app.tenant.context.TenantContext;

import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GrpcService
public class OrderGrpcServiceImpl implements OrderGrpcService {

    private static final Logger LOG = Logger.getLogger(OrderGrpcServiceImpl.class);

    @Inject
    OrderService orderService;

    @Inject
    OrderGrpcMapper mapper;

    @Override
    public Uni<OrderProto> createOrder(CreateOrderRequest request) {
        return Uni.createFrom().item(() -> {
            try {
                List<OrderItemEntity> items = new ArrayList<>();
                for (OrderItemProto itemProto : request.getItemsList()) {
                    OrderItemEntity item = new OrderItemEntity();
                    item.setProductId(itemProto.getProductId());
                    item.setName(itemProto.getName());
                    item.setQuantity(itemProto.getQuantity());
                    item.setUnitPrice(BigDecimal.valueOf(itemProto.getUnitPrice()));
                    items.add(item);
                }

                UUID userId = UUID.fromString(request.getUserId());
                OrderEntity entity = orderService.createOrder(userId, items);

                LOG.infof("Order created via gRPC: %s for user %s", entity.getId(), request.getUserId());
                return mapper.toProto(entity);
            } catch (ValidationException e) {
                throw Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException();
            } catch (IllegalArgumentException e) {
                throw Status.INVALID_ARGUMENT.withDescription("Invalid user ID format").asRuntimeException();
            } catch (Exception e) {
                LOG.error("Error in createOrder", e);
                throw Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException();
            }
        });
    }

    @Override
    public Uni<OrderProto> getOrder(GetOrderRequest request) {
        return Uni.createFrom().item(() -> {
            try {
                UUID orderId = UUID.fromString(request.getId());
                OrderEntity order = orderService.findById(orderId);
                return mapper.toProto(order);
            } catch (NotFoundException e) {
                throw Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException();
            } catch (IllegalArgumentException e) {
                throw Status.INVALID_ARGUMENT.withDescription("Invalid order ID format").asRuntimeException();
            } catch (Exception e) {
                LOG.error("Error in getOrder", e);
                throw Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException();
            }
        });
    }

    @Override
    public Uni<ListOrdersResponse> listOrdersByUser(ListOrdersByUserRequest request) {
        return Uni.createFrom().item(() -> {
            try {
                UUID userId = UUID.fromString(request.getUserId());
                int page = request.hasPage() ? request.getPage().getPage() : 0;
                int size = request.hasPage() ? request.getPage().getSize() : 20;
                if (size <= 0) size = 20;

                List<OrderEntity> orders = orderService.listByUserId(userId, page, size);
                long totalCount = orderService.countByUserId(userId);
                int totalPages = (int) Math.ceil((double) totalCount / size);

                return mapper.toListResponse(orders, totalCount, totalPages, page);
            } catch (Exception e) {
                LOG.error("Error in listOrdersByUser", e);
                throw Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException();
            }
        });
    }

    @Override
    public Uni<OrderProto> updateOrderStatus(UpdateOrderStatusRequest request) {
        return Uni.createFrom().item(() -> {
            try {
                UUID orderId = UUID.fromString(request.getId());
                OrderEntity.OrderStatus newStatus = OrderEntity.OrderStatus.valueOf(request.getStatus());
                OrderEntity order = orderService.updateStatus(orderId, newStatus, request.getReason());

                LOG.infof("Order %s status updated to %s via gRPC", orderId, newStatus);
                return mapper.toProto(order);
            } catch (NotFoundException e) {
                throw Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException();
            } catch (IllegalArgumentException e) {
                throw Status.INVALID_ARGUMENT.withDescription("Invalid status: " + request.getStatus()).asRuntimeException();
            } catch (Exception e) {
                LOG.error("Error in updateOrderStatus", e);
                throw Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException();
            }
        });
    }

    @Override
    public Multi<OrderProto> streamOrderUpdates(GetOrderRequest request) {
        return Multi.createFrom().emitter(emitter -> {
            try {
                UUID orderId = UUID.fromString(request.getId());
                OrderEntity order = orderService.findById(orderId);
                emitter.emit(mapper.toProto(order));
                emitter.complete();
            } catch (NotFoundException e) {
                emitter.fail(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
            } catch (Exception e) {
                emitter.fail(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
            }
        });
    }
}
