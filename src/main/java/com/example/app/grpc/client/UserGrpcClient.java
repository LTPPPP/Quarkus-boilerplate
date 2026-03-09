package com.example.app.grpc.client;

import com.example.app.grpc.proto.GetUserRequest;
import com.example.app.grpc.proto.UserExistsRequest;
import com.example.app.grpc.proto.UserExistsResponse;
import com.example.app.grpc.proto.UserGrpcService;
import com.example.app.grpc.proto.UserProto;

import io.quarkus.grpc.GrpcClient;
import io.smallrye.mutiny.Uni;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Optional;

@ApplicationScoped
public class UserGrpcClient {

    private static final Logger LOG = Logger.getLogger(UserGrpcClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Inject
    @GrpcClient("user-service")
    UserGrpcService userGrpcService;

    public boolean checkUserExists(String email) {
        try {
            UserExistsRequest request = UserExistsRequest.newBuilder()
                    .setEmail(email)
                    .build();

            UserExistsResponse response = userGrpcService.userExists(request)
                    .await().atMost(TIMEOUT);

            LOG.debugf("UserExists check for email=%s: exists=%b", email, response.getExists());
            return response.getExists();
        } catch (Exception e) {
            LOG.errorf(e, "gRPC call failed: checkUserExists for email=%s", email);
            return false;
        }
    }

    public Optional<UserProto> getUserById(String userId) {
        try {
            GetUserRequest request = GetUserRequest.newBuilder()
                    .setId(userId)
                    .build();

            UserProto response = userGrpcService.getUser(request)
                    .await().atMost(TIMEOUT);

            LOG.debugf("GetUser result for id=%s: found=%b", userId, true);
            return Optional.of(response);
        } catch (Exception e) {
            LOG.warnf("gRPC call failed: getUserById for id=%s: %s", userId, e.getMessage());
            return Optional.empty();
        }
    }

    public Uni<UserProto> getUserByIdAsync(String userId) {
        GetUserRequest request = GetUserRequest.newBuilder()
                .setId(userId)
                .build();

        return userGrpcService.getUser(request)
                .ifNoItem().after(TIMEOUT).fail();
    }
}
