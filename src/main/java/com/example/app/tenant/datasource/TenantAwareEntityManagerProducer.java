package com.example.app.tenant.datasource;

import com.example.app.tenant.context.TenantContext;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.SQLException;

@ApplicationScoped
public class TenantAwareEntityManagerProducer {

    private static final Logger LOG = Logger.getLogger(TenantAwareEntityManagerProducer.class);

    @Inject
    TenantAwareDataSourceProvider dataSourceProvider;

    @ConfigProperty(name = "app.multitenancy.strategy", defaultValue = "SCHEMA")
    String strategy;

    @Inject
    @PersistenceUnit
    EntityManagerFactory entityManagerFactory;

    @Produces
    @RequestScoped
    public EntityManager produceEntityManager() {
        EntityManager em = entityManagerFactory.createEntityManager();

        if ("SCHEMA".equalsIgnoreCase(strategy)) {
            String tenantId = TenantContext.getCurrentTenant();
            if (!TenantContext.DEFAULT_TENANT.equals(tenantId)) {
                try {
                    em.getTransaction().begin();
                    String schemaName = dataSourceProvider.resolveSchemaName(tenantId);
                    em.createNativeQuery("SET search_path TO " + schemaName + ", public").executeUpdate();
                    em.getTransaction().commit();
                    LOG.debugf("EntityManager schema set to %s for tenant %s", schemaName, tenantId);
                } catch (Exception e) {
                    LOG.errorf(e, "Failed to set schema for tenant %s", tenantId);
                    if (em.getTransaction().isActive()) {
                        em.getTransaction().rollback();
                    }
                }
            }
        }

        return em;
    }

    public void disposeEntityManager(@Disposes EntityManager em) {
        if (em.isOpen()) {
            em.close();
            LOG.debug("EntityManager disposed");
        }
    }
}
