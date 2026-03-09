package com.example.app.grpc;

import com.example.app.grpc.client.UserGrpcClient;
import com.example.app.grpc.proto.UserProto;
import com.example.app.tenant.context.TenantContext;

import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class GrpcClientTest {

    @Inject
    UserGrpcClient userGrpcClient;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant("client-test-tenant");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testCheckUserExistsReturnsFalseForNonExistent() {
        boolean exists = userGrpcClient.checkUserExists("nonexistent-" + System.currentTimeMillis() + "@test.com");
        assertFalse(exists, "Non-existent user email should return false");
    }

    @Test
    void testGetUserByIdReturnsEmptyForNonExistent() {
        Optional<UserProto> result = userGrpcClient.getUserById("00000000-0000-0000-0000-000000000000");
        assertNotNull(result, "Result Optional should not be null");
        assertTrue(result.isEmpty(), "Non-existent user ID should return empty Optional");
    }

    @Test
    void testCheckUserExistsWithNullEmail() {
        boolean exists = userGrpcClient.checkUserExists("");
        assertFalse(exists, "Empty email should return false");
    }

    @Test
    void testGetUserByIdWithInvalidId() {
        Optional<UserProto> result = userGrpcClient.getUserById("not-a-uuid");
        assertNotNull(result, "Result should not be null even for invalid ID");
        assertTrue(result.isEmpty(), "Invalid UUID should return empty Optional");
    }
}
