package com.example.app.grpc.service;

import com.example.app.grpc.client.UserGrpcClient;
import com.example.app.grpc.proto.CreateUserRequest;
import com.example.app.grpc.proto.ListUsersRequest;
import com.example.app.grpc.proto.ListUsersResponse;
import com.example.app.grpc.proto.PageRequest;
import com.example.app.grpc.proto.UserGrpcService;
import com.example.app.grpc.proto.UserProto;

import io.quarkus.grpc.GrpcClient;
import io.smallrye.mutiny.Uni;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Path("/api/v1/grpc")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GrpcGatewayResource {

    private static final Logger LOG = Logger.getLogger(GrpcGatewayResource.class);

    @Inject
    @GrpcClient("user-service")
    UserGrpcService userGrpcService;

    @Inject
    UserGrpcClient userGrpcClient;

    @POST
    @Path("/users")
    @Timeout(5000)
    @Retry(maxRetries = 2)
    public Uni<Response> createUser(GrpcCreateUserRequest request) {
        LOG.infof("gRPC Gateway: Creating user via gRPC, email=%s", request.email);

        CreateUserRequest grpcRequest = CreateUserRequest.newBuilder()
                .setEmail(request.email)
                .setPassword(request.password)
                .setFullName(request.fullName)
                .setRole(request.role != null ? request.role : "USER")
                .build();

        return userGrpcService.createUser(grpcRequest)
                .onItem().transform(proto -> Response.status(Response.Status.CREATED)
                        .entity(protoToMap(proto))
                        .build())
                .onFailure().recoverWithItem(t -> {
                    LOG.errorf(t, "gRPC Gateway: createUser failed");
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity(Map.of("error", t.getMessage()))
                            .build();
                });
    }

    @GET
    @Path("/users/{id}")
    @Timeout(5000)
    @Retry(maxRetries = 2)
    public Response getUser(@PathParam("id") String id) {
        LOG.infof("gRPC Gateway: Getting user via gRPC, id=%s", id);

        Optional<UserProto> result = userGrpcClient.getUserById(id);
        if (result.isPresent()) {
            return Response.ok(protoToMap(result.get())).build();
        }
        return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "User not found: " + id))
                .build();
    }

    @GET
    @Path("/users")
    @Timeout(5000)
    @Retry(maxRetries = 2)
    public Uni<Response> listUsers(@QueryParam("page") int page,
                                    @QueryParam("size") int size,
                                    @QueryParam("tenantId") String tenantId) {
        int actualSize = size > 0 ? size : 20;

        ListUsersRequest grpcRequest = ListUsersRequest.newBuilder()
                .setPage(PageRequest.newBuilder()
                        .setPage(page)
                        .setSize(actualSize)
                        .build())
                .setTenantId(tenantId != null ? tenantId : "")
                .build();

        return userGrpcService.listUsers(grpcRequest)
                .onItem().transform(response -> Response.ok(Map.of(
                        "users", response.getUsersList().stream().map(this::protoToMap).toList(),
                        "pageInfo", Map.of(
                                "totalElements", response.getPageInfo().getTotalElements(),
                                "totalPages", response.getPageInfo().getTotalPages(),
                                "currentPage", response.getPageInfo().getCurrentPage()
                        )
                )).build())
                .onFailure().recoverWithItem(t -> {
                    LOG.errorf(t, "gRPC Gateway: listUsers failed");
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity(Map.of("error", t.getMessage()))
                            .build();
                });
    }

    @GET
    @Path("/users/exists")
    @Timeout(5000)
    public Response checkUserExists(@QueryParam("email") String email) {
        boolean exists = userGrpcClient.checkUserExists(email);
        return Response.ok(Map.of("email", email, "exists", exists)).build();
    }

    private Map<String, Object> protoToMap(UserProto proto) {
        return Map.of(
                "id", proto.getId(),
                "email", proto.getEmail(),
                "fullName", proto.getFullName(),
                "role", proto.getRole(),
                "status", proto.getStatus()
        );
    }

    public static class GrpcCreateUserRequest {
        public String email;
        public String password;
        public String fullName;
        public String role;
    }
}
