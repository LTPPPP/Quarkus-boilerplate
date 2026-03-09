package com.example.app.grpc.service;

import com.example.app.domain.UserEntity;
import com.example.app.exception.ConflictException;
import com.example.app.exception.NotFoundException;
import com.example.app.exception.ValidationException;
import com.example.app.grpc.mapper.UserGrpcMapper;
import com.example.app.grpc.proto.ChatMessage;
import com.example.app.grpc.proto.CreateUserRequest;
import com.example.app.grpc.proto.DeleteUserRequest;
import com.example.app.grpc.proto.Empty;
import com.example.app.grpc.proto.GetUserRequest;
import com.example.app.grpc.proto.ListUsersRequest;
import com.example.app.grpc.proto.ListUsersResponse;
import com.example.app.grpc.proto.Timestamp;
import com.example.app.grpc.proto.UpdateUserRequest;
import com.example.app.grpc.proto.UserExistsRequest;
import com.example.app.grpc.proto.UserExistsResponse;
import com.example.app.grpc.proto.UserGrpcService;
import com.example.app.grpc.proto.UserProto;
import com.example.app.service.UserService;
import com.example.app.tenant.context.TenantContext;

import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@GrpcService
public class UserGrpcServiceImpl implements UserGrpcService {

    private static final Logger LOG = Logger.getLogger(UserGrpcServiceImpl.class);

    @Inject
    UserService userService;

    @Inject
    UserGrpcMapper mapper;

    @Override
    public Uni<UserProto> getUser(GetUserRequest request) {
        return Uni.createFrom().item(() -> {
            try {
                UUID userId = UUID.fromString(request.getId());
                UserEntity user = userService.findById(userId);
                return mapper.toProto(user);
            } catch (NotFoundException e) {
                throw Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException();
            } catch (IllegalArgumentException e) {
                throw Status.INVALID_ARGUMENT.withDescription("Invalid user ID format").asRuntimeException();
            } catch (Exception e) {
                LOG.error("Error in getUser", e);
                throw Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException();
            }
        });
    }

    @Override
    public Uni<ListUsersResponse> listUsers(ListUsersRequest request) {
        return Uni.createFrom().item(() -> {
            try {
                int page = request.hasPage() ? request.getPage().getPage() : 0;
                int size = request.hasPage() ? request.getPage().getSize() : 20;
                if (size <= 0) size = 20;
                if (size > 100) size = 100;

                String tenantId = request.getTenantId().isEmpty()
                        ? TenantContext.getCurrentTenant()
                        : request.getTenantId();

                List<UserEntity> users = userService.listByTenantId(tenantId, page, size);
                long totalCount = userService.countByTenantId(tenantId);
                int totalPages = (int) Math.ceil((double) totalCount / size);

                return mapper.toListResponse(users, totalCount, totalPages, page);
            } catch (Exception e) {
                LOG.error("Error in listUsers", e);
                throw Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException();
            }
        });
    }

    @Override
    public Uni<UserProto> createUser(CreateUserRequest request) {
        return Uni.createFrom().item(() -> {
            try {
                UserEntity entity = userService.createUser(
                        request.getEmail(),
                        request.getPassword(),
                        request.getFullName(),
                        request.getRole().isEmpty() ? "USER" : request.getRole()
                );
                LOG.infof("User created via gRPC: %s", entity.getId());
                return mapper.toProto(entity);
            } catch (ValidationException e) {
                throw Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException();
            } catch (ConflictException e) {
                throw Status.ALREADY_EXISTS.withDescription(e.getMessage()).asRuntimeException();
            } catch (Exception e) {
                LOG.error("Error in createUser", e);
                throw Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException();
            }
        });
    }

    @Override
    public Uni<UserProto> updateUser(UpdateUserRequest request) {
        return Uni.createFrom().item(() -> {
            try {
                UUID userId = UUID.fromString(request.getId());
                UserEntity user = userService.updateUser(
                        userId,
                        request.getFullName().isEmpty() ? null : request.getFullName(),
                        request.getRole().isEmpty() ? null : request.getRole(),
                        request.getIsActive()
                );
                LOG.infof("User updated via gRPC: %s", userId);
                return mapper.toProto(user);
            } catch (NotFoundException e) {
                throw Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException();
            } catch (Exception e) {
                LOG.error("Error in updateUser", e);
                throw Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException();
            }
        });
    }

    @Override
    public Uni<Empty> deleteUser(DeleteUserRequest request) {
        return Uni.createFrom().item(() -> {
            try {
                UUID userId = UUID.fromString(request.getId());
                userService.deleteUser(userId);
                LOG.infof("User deleted via gRPC: %s", userId);
                return Empty.getDefaultInstance();
            } catch (NotFoundException e) {
                throw Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException();
            } catch (Exception e) {
                LOG.error("Error in deleteUser", e);
                throw Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException();
            }
        });
    }

    @Override
    public Uni<UserExistsResponse> userExists(UserExistsRequest request) {
        return Uni.createFrom().item(() -> {
            boolean exists = userService.existsByEmail(request.getEmail());
            UserExistsResponse.Builder builder = UserExistsResponse.newBuilder().setExists(exists);
            if (exists) {
                try {
                    UserEntity user = userService.findByEmail(request.getEmail());
                    builder.setUserId(user.getId().toString());
                } catch (NotFoundException ignored) {
                }
            }
            return builder.build();
        });
    }

    @Override
    public Multi<UserProto> streamUsers(ListUsersRequest request) {
        return Multi.createFrom().emitter(emitter -> {
            try {
                String tenantId = request.getTenantId().isEmpty()
                        ? TenantContext.getCurrentTenant()
                        : request.getTenantId();

                int page = 0;
                int size = request.hasPage() && request.getPage().getSize() > 0
                        ? request.getPage().getSize() : 50;

                List<UserEntity> batch;
                do {
                    batch = userService.listByTenantId(tenantId, page, size);
                    for (UserEntity user : batch) {
                        emitter.emit(mapper.toProto(user));
                    }
                    page++;
                } while (batch.size() == size);

                emitter.complete();
            } catch (Exception e) {
                emitter.fail(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
            }
        });
    }

    @Override
    public Uni<ListUsersResponse> batchCreateUsers(Multi<CreateUserRequest> requests) {
        return requests.collect().asList().onItem().transform(requestList -> {
            List<UserEntity> createdUsers = new java.util.ArrayList<>();
            for (CreateUserRequest req : requestList) {
                try {
                    UserEntity entity = userService.createUser(
                            req.getEmail(),
                            req.getPassword(),
                            req.getFullName(),
                            req.getRole().isEmpty() ? "USER" : req.getRole()
                    );
                    createdUsers.add(entity);
                } catch (ConflictException e) {
                    LOG.warnf("Skipping duplicate user in batch: %s", req.getEmail());
                }
            }
            return mapper.toListResponse(createdUsers, createdUsers.size(), 1, 0);
        });
    }

    @Override
    public Multi<ChatMessage> chat(Multi<ChatMessage> requests) {
        return Multi.createFrom().emitter(emitter -> {
            requests.subscribe().with(
                    message -> {
                        String tenantId = message.getTenantId().isEmpty()
                                ? TenantContext.getCurrentTenant()
                                : message.getTenantId();

                        LOG.infof("Chat message from %s in tenant %s: %s",
                                message.getSenderId(), tenantId, message.getContent());

                        Instant now = Instant.now();
                        ChatMessage response = ChatMessage.newBuilder()
                                .setSenderId("system")
                                .setContent("Received: " + message.getContent())
                                .setTenantId(tenantId)
                                .setSentAt(Timestamp.newBuilder()
                                        .setSeconds(now.getEpochSecond())
                                        .setNanos(now.getNano())
                                        .build())
                                .build();
                        emitter.emit(response);
                    },
                    emitter::fail,
                    emitter::complete
            );
        });
    }
}
