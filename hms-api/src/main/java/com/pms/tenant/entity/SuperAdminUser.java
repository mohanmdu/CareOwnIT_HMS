package com.pms.tenant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Deliberately a separate login table from general_user, not a flag on it -
 * a Super Admin belongs to no Client and must never be reachable via the
 * tenant-scoped authorization path (see com.pms.security.
 * ModuleAuthorizationManager and com.pms.superadmin.SuperAdminLoginService).
 */
@Entity
@Table(name = "super_admin_user")
@Getter
@Setter
@NoArgsConstructor
public class SuperAdminUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
