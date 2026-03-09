package com.example.app.resource;

import com.example.app.domain.OrderEntity;
import com.example.app.domain.OrderItemEntity;
import com.example.app.service.OrderService;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/api/v1/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {

    @Inject
    OrderService orderService;

    @POST
    public Response createOrder(CreateOrderDto dto) {
        List<OrderItemEntity> items = new ArrayList<>();
        if (dto.items != null) {
            for (OrderItemDto itemDto : dto.items) {
                OrderItemEntity item = new OrderItemEntity();
                item.setProductId(itemDto.productId);
                item.setName(itemDto.name);
                item.setQuantity(itemDto.quantity);
                item.setUnitPrice(itemDto.unitPrice);
                items.add(item);
            }
        }

        OrderEntity order = orderService.createOrder(dto.userId, items);
        return Response.created(URI.create("/api/v1/orders/" + order.getId())).entity(order).build();
    }

    @GET
    @Path("/{id}")
    public Response getOrder(@PathParam("id") UUID id) {
        OrderEntity order = orderService.findById(id);
        return Response.ok(order).build();
    }

    @GET
    @Path("/user/{userId}")
    public Response listOrdersByUser(@PathParam("userId") UUID userId,
                                      @QueryParam("page") @DefaultValue("0") int page,
                                      @QueryParam("size") @DefaultValue("20") int size) {
        List<OrderEntity> orders = orderService.listByUserId(userId, page, size);
        long total = orderService.countByUserId(userId);
        return Response.ok(Map.of("orders", orders, "total", total, "page", page, "size", size)).build();
    }

    @PUT
    @Path("/{id}/status")
    public Response updateOrderStatus(@PathParam("id") UUID id, UpdateStatusDto dto) {
        OrderEntity.OrderStatus newStatus = OrderEntity.OrderStatus.valueOf(dto.status);
        OrderEntity order = orderService.updateStatus(id, newStatus, dto.reason);
        return Response.ok(order).build();
    }

    public static class CreateOrderDto {
        public UUID userId;
        public List<OrderItemDto> items;
    }

    public static class OrderItemDto {
        public String productId;
        public String name;
        public int quantity;
        public BigDecimal unitPrice;
    }

    public static class UpdateStatusDto {
        public String status;
        public String reason;
    }
}
