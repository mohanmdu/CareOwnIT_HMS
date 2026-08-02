package com.pms.masters.entity;

import com.pms.common.Auditable;
import com.pms.tenant.entity.Client;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Admin-facing directory of who has system access and their Role - a
 * "General Users Master" record that also doubles as the login credential
 * since V73 (username/passwordHash/mustChangePassword). Real authentication
 * (see com.pms.security.LoginService) looks this entity up by username.
 */
@Entity
@Table(name = "general_user", uniqueConstraints = @UniqueConstraint(columnNames = {"client_id", "username"}))
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

    /** The tenant this user belongs to (Phase A of the multi-tenant licensing plan) - set once at creation (see GeneralUserService.create()) from the creating admin's own client, never changed afterward. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(nullable = false)
    private boolean active = true;

    /** Unique per client (uq_general_user_client_username, see V89), not globally - see the multi-tenant licensing plan's Decisions Confirmed §1. */
    @Column(nullable = false)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** True right after an admin creates the account or resets its password - cleared the moment the user changes it themselves. Enforced server-side by ModuleAuthorizationManager, not just a frontend redirect. */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = true;
}
