package com.example.app.exception;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Map;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class);

    @Override
    public Response toResponse(Throwable exception) {
        if (exception instanceof NotFoundException nfe) {
            return buildResponse(Response.Status.NOT_FOUND, nfe.getMessage(), Map.of(
                    "resourceType", nfe.getResourceType(),
                    "identifier", nfe.getIdentifier()
            ));
        }
        if (exception instanceof ValidationException ve) {
            return buildResponse(Response.Status.BAD_REQUEST, ve.getMessage(), Map.of(
                    "violations", ve.getViolations()
            ));
        }
        if (exception instanceof ConflictException ce) {
            return buildResponse(Response.Status.CONFLICT, ce.getMessage(), Map.of(
                    "resourceType", ce.getResourceType(),
                    "conflictField", ce.getConflictField()
            ));
        }
        if (exception instanceof UnauthorizedException) {
            return buildResponse(Response.Status.UNAUTHORIZED, exception.getMessage(), Map.of());
        }
        if (exception instanceof QuotaExceededException qe) {
            return buildResponse(Response.Status.TOO_MANY_REQUESTS, qe.getMessage(), Map.of(
                    "tenantId", qe.getTenantId(),
                    "quotaType", qe.getQuotaType()
            ));
        }

        LOG.error("Unhandled exception", exception);
        return buildResponse(Response.Status.INTERNAL_SERVER_ERROR, "An unexpected error occurred", Map.of());
    }

    private Response buildResponse(Response.Status status, String message, Map<String, Object> details) {
        Map<String, Object> body = Map.of(
                "status", status.getStatusCode(),
                "error", status.getReasonPhrase(),
                "message", message,
                "details", details,
                "timestamp", Instant.now().toString()
        );
        return Response.status(status).entity(body).build();
    }
}
