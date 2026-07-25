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
}
