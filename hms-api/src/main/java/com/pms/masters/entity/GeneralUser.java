package com.pms.masters.entity;

import com.pms.common.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Admin-facing directory of who has system access and their Role - a
 * "General Users Master" record that also doubles as the login credential
 * since V73 (username/passwordHash/mustChangePassword). Real authentication
 * (see com.pms.security.LoginService) looks this entity up by username.
 *
 * No client/tenant reference on this entity - see the "Database-per-Client
 * Architecture" plan (Phase B). Each client has its own dedicated database,
 * so every row in this table already implicitly belongs to exactly one
 * client by construction; a client_id column would be redundant (this
 * briefly existed as Phase A's client_id, reverted once Phase B's
 * database-per-client model made it unnecessary).
 */
@Entity
@Table(name = "general_user")
@Getter
@Setter
@NoArgsConstructor
public class GeneralUser extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "mobile_number", nullable = false)
    private String mobileNumber;

    @Column
    private String email;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** True right after an admin creates the account or resets its password - cleared the moment the user changes it themselves. Enforced server-side by ModuleAuthorizationManager, not just a frontend redirect. */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = true;
}
