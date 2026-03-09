package com.example.app.resource;

import com.example.app.domain.UserEntity;
import com.example.app.service.UserService;
import com.example.app.tenant.context.TenantContext;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
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

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/api/v1/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

    @Inject
    UserService userService;

    @POST
    public Response createUser(CreateUserDto dto) {
        UserEntity user = userService.createUser(dto.email, dto.password, dto.fullName, dto.role);
        return Response.created(URI.create("/api/v1/users/" + user.getId())).entity(user).build();
    }

    @GET
    public Response listUsers(@QueryParam("page") @DefaultValue("0") int page,
                               @QueryParam("size") @DefaultValue("20") int size) {
        String tenantId = TenantContext.getCurrentTenant();
        List<UserEntity> users = userService.listByTenantId(tenantId, page, size);
        long total = userService.countByTenantId(tenantId);
        return Response.ok(Map.of("users", users, "total", total, "page", page, "size", size)).build();
    }

    @GET
    @Path("/{id}")
    public Response getUser(@PathParam("id") UUID id) {
        UserEntity user = userService.findById(id);
        return Response.ok(user).build();
    }

    @PUT
    @Path("/{id}")
    public Response updateUser(@PathParam("id") UUID id, UpdateUserDto dto) {
        UserEntity user = userService.updateUser(id, dto.fullName, dto.role, dto.isActive);
        return Response.ok(user).build();
    }

    @DELETE
    @Path("/{id}")
    public Response deleteUser(@PathParam("id") UUID id) {
        userService.deleteUser(id);
        return Response.noContent().build();
    }

    public static class CreateUserDto {
        public String email;
        public String password;
        public String fullName;
        public String role;
    }

    public static class UpdateUserDto {
        public String fullName;
        public String role;
        public boolean isActive = true;
    }
}
