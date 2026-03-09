package com.example.app.grpc.interceptor;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Base64;
import java.util.Set;

@ApplicationScoped
public class AuthInterceptor implements ServerInterceptor {

    private static final Logger LOG = Logger.getLogger(AuthInterceptor.class);

    public static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    public static final Context.Key<String> USER_ID_CTX = Context.key("userId");
    public static final Context.Key<String> USER_ROLE_CTX = Context.key("userRole");

    private static final Set<String> SKIP_AUTH_METHODS = Set.of(
            "com.example.app.grpc.UserGrpcService/UserExists",
            "grpc.health.v1.Health/Check",
            "grpc.health.v1.Health/Watch",
            "grpc.reflection.v1alpha.ServerReflection/ServerReflectionInfo"
    );

    @ConfigProperty(name = "mp.jwt.verify.publickey.location", defaultValue = "publicKey.pem")
    String publicKeyLocation;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String fullMethodName = call.getMethodDescriptor().getFullMethodName();

        if (SKIP_AUTH_METHODS.contains(fullMethodName)) {
            return next.startCall(call, headers);
        }

        String authHeader = headers.get(AUTHORIZATION_KEY);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            call.close(Status.UNAUTHENTICATED.withDescription("Missing or invalid authorization token"), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        try {
            String token = authHeader.substring(7);
            TokenClaims claims = validateToken(token);

            Context ctx = Context.current()
                    .withValue(USER_ID_CTX, claims.userId)
                    .withValue(USER_ROLE_CTX, claims.role);

            LOG.debugf("gRPC auth: userId=%s, role=%s, method=%s",
                    claims.userId, claims.role, fullMethodName);

            return Contexts.interceptCall(ctx, call, headers, next);
        } catch (Exception e) {
            LOG.warnf("gRPC auth failed for method %s: %s", fullMethodName, e.getMessage());
            call.close(Status.UNAUTHENTICATED.withDescription("Token validation failed: " + e.getMessage()), new Metadata());
            return new ServerCall.Listener<>() {};
        }
    }

    private TokenClaims validateToken(String token) {
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid JWT format");
        }

        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));

        String userId = extractClaim(payloadJson, "sub");
        String role = extractClaim(payloadJson, "role");

        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Missing 'sub' claim in token");
        }

        return new TokenClaims(userId, role != null ? role : "USER");
    }

    private String extractClaim(String json, String claim) {
        String searchKey = "\"" + claim + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) return null;

        int colonIndex = json.indexOf(':', keyIndex + searchKey.length());
        if (colonIndex == -1) return null;

        int valueStart = colonIndex + 1;
        while (valueStart < json.length() && json.charAt(valueStart) == ' ') valueStart++;

        if (valueStart >= json.length()) return null;

        if (json.charAt(valueStart) == '"') {
            int valueEnd = json.indexOf('"', valueStart + 1);
            return valueEnd == -1 ? null : json.substring(valueStart + 1, valueEnd);
        }

        int valueEnd = valueStart;
        while (valueEnd < json.length() && json.charAt(valueEnd) != ',' && json.charAt(valueEnd) != '}') valueEnd++;
        return json.substring(valueStart, valueEnd).trim();
    }

    private record TokenClaims(String userId, String role) {}
}
