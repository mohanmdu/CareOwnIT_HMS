package com.pms.tenant.entity;

import com.pms.common.Auditable;
import com.pms.masters.entity.ModuleKey;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import java.util.EnumMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The tenant record - lives in the master database (Phase B - see the
 * "Database-per-Client Architecture" plan), never in a tenant's own
 * database. A single-tenant/offline install (app.deployment.mode=
 * single-tenant, the default - see com.pms.tenant.DeploymentModeProperties)
 * has exactly one Client row in its own local master database, so nothing
 * about this entity is visible to that deployment's own tenant-side users;
 * only Super Admin (see com.pms.superadmin) ever manages Client rows
 * directly. See ClientDatabase for which physical database this Client's
 * data actually lives in.
 */
@Entity
@Table(name = "client")
@Getter
@Setter
@NoArgsConstructor
public class Client extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** Short human-assigned identifier entered at login in multi-tenant mode (see LoginService) to disambiguate which client's username namespace to search. */
    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClientStatus status = ClientStatus.ACTIVE;

    /** Reporting metadata only - see DeploymentType's own doc comment. */
    @Enumerated(EnumType.STRING)
    @Column(name = "deployment_type", nullable = false)
    private DeploymentType deploymentType = DeploymentType.CLOUD;

    /**
     * Which modules this client has purchased - checked fresh on every
     * request by ClientLicenseService (short-TTL cached), never trusted
     * from the JWT's licensedModules claim, which exists only for frontend
     * UI convenience. Same @ElementCollection pattern as
     * Role.permittedModules / role_module_permission (V73).
     */
    @ElementCollection
    @CollectionTable(name = "client_module", joinColumns = @JoinColumn(name = "client_id"))
    @MapKeyColumn(name = "module_key")
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "enabled", nullable = false)
    private Map<ModuleKey, Boolean> moduleLicenses = new EnumMap<>(ModuleKey.class);
}
