package com.pms.tenant.entity;

import com.pms.common.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Connection details for one Client's dedicated database - what Phase A
 * never needed (a single hardcoded DB per deployment). {@code clientId} is
 * both the primary key and the foreign key to {@code client.id} (one-to-one
 * by construction - every Client has at most one database).
 *
 * {@code encryptedPassword} is AES-256-GCM ciphertext, encrypted with the
 * deployment's {@code TENANT_DB_ENCRYPTION_KEY} root key (see
 * TenantSecretService) - the plaintext password is never stored, logged, or
 * held longer than the moment a connection pool is created from it.
 */
@Entity
@Table(name = "client_database")
@Getter
@Setter
@NoArgsConstructor
public class ClientDatabase extends Auditable {

    @Id
    @Column(name = "client_id")
    private Long clientId;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private int port = 3306;

    @Column(name = "schema_name", nullable = false)
    private String schemaName;

    @Column(nullable = false)
    private String username;

    @Column(name = "encrypted_password", nullable = false)
    private byte[] encryptedPassword;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClientDatabaseStatus status = ClientDatabaseStatus.PROVISIONING;

    /** Last Flyway version successfully applied to this tenant's database - see the deploy-time migration sweep. Null until the first successful migration run. */
    @Column(name = "schema_version")
    private String schemaVersion;

    /** The JDBC URL this row describes - built once here rather than re-assembled at every call site. */
    public String jdbcUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/" + schemaName + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    }
}
