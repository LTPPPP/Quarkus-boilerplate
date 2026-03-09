package com.example.app.tenant.resource;

import com.example.app.tenant.domain.TenantEntity;
import com.example.app.tenant.service.TenantProvisioningService;
import com.example.app.tenant.service.TenantService;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Path("/api/v1/admin/tenants")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("SUPER_ADMIN")
public class TenantResource {

    @Inject
    TenantService tenantService;

    @Inject
    TenantProvisioningService provisioningService;

    @POST
    public Response createTenant(CreateTenantRequest request) {
        TenantEntity tenant = provisioningService.provisionTenant(
                request.tenantId,
                request.name,
                request.plan != null ? TenantEntity.TenantPlan.valueOf(request.plan) : null,
                request.maxUsers,
                request.storageQuotaGb
        );
        return Response.created(URI.create("/api/v1/admin/tenants/" + tenant.getTenantId()))
                .entity(tenant)
                .build();
    }

    @GET
    public Response listTenants() {
        List<TenantEntity> tenants = tenantService.listTenants();
        return Response.ok(tenants).build();
    }

    @GET
    @Path("/{tenantId}")
    public Response getTenant(@PathParam("tenantId") String tenantId) {
        TenantEntity tenant = tenantService.getTenant(tenantId);
        return Response.ok(tenant).build();
    }

    @PUT
    @Path("/{tenantId}")
    public Response updateTenant(@PathParam("tenantId") String tenantId, UpdateTenantRequest request) {
        TenantEntity tenant = tenantService.updateTenant(
                tenantId,
                request.name,
                request.plan != null ? TenantEntity.TenantPlan.valueOf(request.plan) : null,
                request.maxUsers,
                request.storageQuotaGb,
                request.features
        );
        return Response.ok(tenant).build();
    }

    @POST
    @Path("/{tenantId}/suspend")
    public Response suspendTenant(@PathParam("tenantId") String tenantId) {
        TenantEntity tenant = provisioningService.suspendTenant(tenantId);
        return Response.ok(tenant).build();
    }

    @POST
    @Path("/{tenantId}/activate")
    public Response activateTenant(@PathParam("tenantId") String tenantId) {
        TenantEntity tenant = provisioningService.activateTenant(tenantId);
        return Response.ok(tenant).build();
    }

    @DELETE
    @Path("/{tenantId}")
    public Response deleteTenant(@PathParam("tenantId") String tenantId) {
        provisioningService.deleteTenant(tenantId);
        return Response.noContent().build();
    }

    @GET
    @Path("/{tenantId}/stats")
    public Response getTenantStats(@PathParam("tenantId") String tenantId) {
        Map<String, Object> stats = tenantService.getTenantStats(tenantId);
        return Response.ok(stats).build();
    }

    public static class CreateTenantRequest {
        public String tenantId;
        public String name;
        public String plan;
        public int maxUsers;
        public int storageQuotaGb;
    }

    public static class UpdateTenantRequest {
        public String name;
        public String plan;
        public int maxUsers;
        public int storageQuotaGb;
        public String features;
    }
}
