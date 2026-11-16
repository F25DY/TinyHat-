package com.hexora.hexora.security;

import java.util.Objects;
import java.util.function.Supplier;

public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static Long currentTenantId() {
        return CURRENT_TENANT.get();
    }

    public static long requireTenantId() {
        Long tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new IllegalStateException("A tenant context is required for tenant-scoped data");
        }
        return tenantId;
    }

    public static <T> T runAs(long tenantId, Supplier<T> action) {
        if (tenantId < 1) {
            throw new IllegalArgumentException("Tenant id must be positive");
        }
        Objects.requireNonNull(action, "Tenant action is required");

        Long previousTenant = CURRENT_TENANT.get();
        if (previousTenant != null && previousTenant != tenantId) {
            throw new IllegalStateException("Cannot replace an active tenant context");
        }

        CURRENT_TENANT.set(tenantId);
        try {
            return action.get();
        } finally {
            if (previousTenant == null) {
                CURRENT_TENANT.remove();
            } else {
                CURRENT_TENANT.set(previousTenant);
            }
        }
    }

    public static void runAs(long tenantId, Runnable action) {
        runAs(tenantId, () -> {
            action.run();
            return null;
        });
    }
}
