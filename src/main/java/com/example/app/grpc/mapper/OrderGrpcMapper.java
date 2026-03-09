package com.example.app.grpc.mapper;

import com.example.app.domain.OrderEntity;
import com.example.app.domain.OrderItemEntity;
import com.example.app.grpc.proto.CreateOrderRequest;
import com.example.app.grpc.proto.ListOrdersResponse;
import com.example.app.grpc.proto.OrderItemProto;
import com.example.app.grpc.proto.OrderProto;
import com.example.app.grpc.proto.PageInfo;
import com.example.app.grpc.proto.Timestamp;

import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class OrderGrpcMapper {

    public OrderProto toProto(OrderEntity entity) {
        OrderProto.Builder builder = OrderProto.newBuilder()
                .setId(entity.getId().toString())
                .setUserId(entity.getUserId().toString())
                .setTotalAmount(entity.getTotalAmount().doubleValue())
                .setStatus(entity.getStatus().name())
                .setTenantId(entity.getTenantId() != null ? entity.getTenantId() : "");

        if (entity.getCreatedAt() != null) {
            builder.setCreatedAt(Timestamp.newBuilder()
                    .setSeconds(entity.getCreatedAt().getEpochSecond())
                    .setNanos(entity.getCreatedAt().getNano())
                    .build());
        }

        if (entity.getItems() != null) {
            for (OrderItemEntity item : entity.getItems()) {
                builder.addItems(toItemProto(item));
            }
        }

        return builder.build();
    }

    public OrderItemProto toItemProto(OrderItemEntity item) {
        return OrderItemProto.newBuilder()
                .setProductId(item.getProductId())
                .setName(item.getName())
                .setQuantity(item.getQuantity())
                .setUnitPrice(item.getUnitPrice().doubleValue())
                .build();
    }

    public OrderEntity toEntity(CreateOrderRequest request) {
        OrderEntity entity = new OrderEntity();
        entity.setUserId(UUID.fromString(request.getUserId()));
        entity.setTenantId(request.getTenantId());
        entity.setStatus(OrderEntity.OrderStatus.PLACED);

        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemProto itemProto : request.getItemsList()) {
            OrderItemEntity item = new OrderItemEntity();
            item.setProductId(itemProto.getProductId());
            item.setName(itemProto.getName());
            item.setQuantity(itemProto.getQuantity());
            item.setUnitPrice(BigDecimal.valueOf(itemProto.getUnitPrice()));
            entity.addItem(item);
            total = total.add(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        entity.setTotalAmount(total);
        return entity;
    }

    public Instant toInstant(Timestamp ts) {
        return Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos());
    }

    public ListOrdersResponse toListResponse(List<OrderEntity> orders, long totalElements,
                                              int totalPages, int currentPage) {
        ListOrdersResponse.Builder builder = ListOrdersResponse.newBuilder()
                .setPageInfo(PageInfo.newBuilder()
                        .setTotalElements(totalElements)
                        .setTotalPages(totalPages)
                        .setCurrentPage(currentPage)
                        .build());

        for (OrderEntity order : orders) {
            builder.addOrders(toProto(order));
        }

        return builder.build();
    }
}
