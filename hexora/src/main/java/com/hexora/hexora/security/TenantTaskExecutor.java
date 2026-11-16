package com.hexora.hexora.security;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantTaskExecutor {

    private final EntityManager entityManager;

    public TenantTaskExecutor(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    public void execute(long tenantId, Runnable task) {
        TenantContext.runAs(tenantId, () -> {
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("accountFilter").setParameter("tenantId", tenantId);
            try {
                task.run();
                entityManager.flush();
            } finally {
                session.disableFilter("accountFilter");
            }
        });
    }
}
