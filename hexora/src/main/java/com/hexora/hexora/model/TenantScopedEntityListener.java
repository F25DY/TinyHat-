package com.hexora.hexora.model;

import com.hexora.hexora.security.TenantContext;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

public class TenantScopedEntityListener {

    @PrePersist
    public void assignTenant(TenantScopedEntity entity) {
        long tenantId = TenantContext.requireTenantId();
        Long entityTenantId = entity.getCuentaId();
        if (entityTenantId != null && entityTenantId != tenantId) {
            throw new IllegalStateException("Entity tenant does not match the authenticated tenant");
        }
        entity.setCuentaId(tenantId);
    }

    @PreUpdate
    public void preserveTenant(TenantScopedEntity entity) {
        long tenantId = TenantContext.requireTenantId();
        if (!Long.valueOf(tenantId).equals(entity.getCuentaId())) {
            throw new IllegalStateException("Entity tenant does not match the authenticated tenant");
        }
    }
}
