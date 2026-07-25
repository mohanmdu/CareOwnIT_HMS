package com.pms.masters.entity;

import com.pms.common.Auditable;
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
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Replaces the legacy com.pms.model.RoleMaster (table roles_master) - see
 * migration doc §4.6. Renamed for clarity since "Userdetails" in the legacy
 * schema is actually an audit-log table, not a user-profile/role table.
 */
@Entity
@Table(name = "role")
@Getter
@Setter
@NoArgsConstructor
public class Role extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    /**
     * Which sidenav groups (NAV_GROUPS in nav-config.ts) this role can see -
     * intersected with the deployment's package tier at nav-render time (a
     * BASIC-tier deployment never shows Pharmacy regardless of this set).
     * Empty is a legitimate "not configured yet" state, not an error - see
     * ModuleAuthorizationManager for how this gates actual API access.
     */
    @ElementCollection
    @CollectionTable(name = "role_module_permission", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "module_key")
    @Enumerated(EnumType.STRING)
    private Set<ModuleKey> permittedModules = new HashSet<>();

    /**
     * Individual sidenav pages (NavItem.route) this role can see, WITHIN an
     * already-permitted module - a finer-grained restriction layered on top
     * of permittedModules, not a replacement for it. Opaque route strings
     * (e.g. "/registration/patients"), owned and validated by the frontend's
     * NAV_GROUPS, not this backend - see role-list.component.ts.
     *
     * Frontend-only enforcement by design (see the Role & User Management
     * page-level-permissions decision): this backend never reads this set
     * for authorization, only stores and returns it. Empty means "no
     * page-level restriction recorded for any module yet" - the frontend
     * treats an already-granted module with zero routes recorded here as
     * fully open (today's behavior), so every role created before this field
     * existed keeps working unchanged with no migration backfill needed.
     */
    @ElementCollection
    @CollectionTable(name = "role_route_permission", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "route", length = 255)
    private Set<String> permittedRoutes = new HashSet<>();

    /**
     * Where a user with this role lands right after login, instead of the
     * default '/dashboard' - e.g. a front-desk-only "Patient" role that
     * should go straight to the booking screen. Null means "use the default
     * dashboard", not an error - most roles have no reason to override this.
     * Opaque frontend route string, same ownership model as permittedRoutes.
     * If this ever points at a page the role no longer has access to (e.g.
     * after a later edit prunes it), the frontend route guard's own
     * always-granted '/dashboard' fallback recovers gracefully - see
     * auth.guard.ts.
     */
    @Column(name = "default_route")
    private String defaultRoute;
}
