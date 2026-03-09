package com.example.app.grpc.mapper;

import com.example.app.domain.UserEntity;
import com.example.app.grpc.proto.CreateUserRequest;
import com.example.app.grpc.proto.ListUsersResponse;
import com.example.app.grpc.proto.PageInfo;
import com.example.app.grpc.proto.Timestamp;
import com.example.app.grpc.proto.UserProto;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class UserGrpcMapper {

    public UserProto toProto(UserEntity entity) {
        UserProto.Builder builder = UserProto.newBuilder()
                .setId(entity.getId().toString())
                .setEmail(entity.getEmail())
                .setFullName(entity.getFullName())
                .setRole(entity.getRole())
                .setStatus(entity.getStatus().name());

        if (entity.getCreatedAt() != null) {
            builder.setCreatedAt(toTimestamp(entity.getCreatedAt()));
        }
        if (entity.getUpdatedAt() != null) {
            builder.setUpdatedAt(toTimestamp(entity.getUpdatedAt()));
        }

        return builder.build();
    }

    public UserEntity toEntity(CreateUserRequest request) {
        UserEntity entity = new UserEntity();
        entity.setEmail(request.getEmail());
        entity.setPasswordHash(request.getPassword());
        entity.setFullName(request.getFullName());
        entity.setRole(request.getRole().isEmpty() ? "USER" : request.getRole());
        entity.setStatus(UserEntity.UserStatus.ACTIVE);
        return entity;
    }

    public Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    public Instant toInstant(Timestamp ts) {
        return Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos());
    }

    public ListUsersResponse toListResponse(List<UserEntity> users, long totalElements,
                                             int totalPages, int currentPage) {
        ListUsersResponse.Builder builder = ListUsersResponse.newBuilder()
                .setPageInfo(PageInfo.newBuilder()
                        .setTotalElements(totalElements)
                        .setTotalPages(totalPages)
                        .setCurrentPage(currentPage)
                        .build());

        for (UserEntity user : users) {
            builder.addUsers(toProto(user));
        }

        return builder.build();
    }
}
