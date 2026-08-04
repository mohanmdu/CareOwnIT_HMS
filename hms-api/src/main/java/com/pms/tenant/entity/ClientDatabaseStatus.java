package com.pms.tenant.entity;

public enum ClientDatabaseStatus {
    /** Provisioning in progress (schema being created + migrated) - never routed to; see TenantDataSourceRegistry. */
    PROVISIONING,
    /** Fully migrated and safe to route requests to. */
    READY,
    /** Provisioning or a migration failed - never routed to; requires operator attention. */
    FAILED,
    /** Deliberately taken offline (e.g. alongside Client.status=SUSPENDED) - never routed to. */
    SUSPENDED
}
