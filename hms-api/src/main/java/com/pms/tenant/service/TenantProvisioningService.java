package com.pms.tenant.service;

import com.pms.common.EntityNotFoundException;
import com.pms.tenant.entity.Client;
import com.pms.tenant.entity.ClientDatabase;
import com.pms.tenant.entity.ClientDatabaseStatus;
import com.pms.tenant.repository.ClientDatabaseRepository;
import com.pms.tenant.repository.ClientRepository;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Onboards a brand-new client's dedicated database - the Provisioning phase
 * of the "Database-per-Client Architecture" plan's execution plan. A
 * low-frequency, synchronous, Super-Admin-triggered action (see
 * ClientController), not a hot path - real production volume would still
 * be a handful of these a day at most, so there's no queueing/async
 * machinery here, just a straightforward multi-step operation with a
 * PROVISIONING -&gt; READY|FAILED state machine on ClientDatabase.status.
 * TenantDataSourceRegistry only ever routes to a READY row, so a half-
 * provisioned database is never reachable by ordinary request traffic.
 *
 * Runs the SAME classpath:db/migration scripts every existing tenant
 * database already has (see TenantFlywayRunner) - a new client starts on
 * an up-to-date schema, not an empty one waiting for the next deploy's
 * sweep.
 *
 * Uses a separate, more privileged admin credential
 * (app.tenant-provisioning.admin-*) than the app's normal tenant query
 * traffic (app.tenant-db.*) - CREATE DATABASE/CREATE USER is a meaningfully
 * bigger blast-radius operation than anything ordinary request handling
 * needs, so it's deliberately scoped to this one service and never reused
 * elsewhere (see the plan's Risks section on provisioning credentials).
 */
@Service
public class TenantProvisioningService {

    /** Schema/username identifiers can't be parameterized in DDL (CREATE DATABASE/CREATE USER aren't queries) - this is what stands between Client.code and SQL injection into that DDL. ClientDto's own @Pattern validation is the first line of defense; this is the second, since this service must never trust that validation already ran. */
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[A-Za-z0-9_]{2,40}$");

    private static final int GENERATED_PASSWORD_BYTES = 24;

    private final ClientRepository clientRepository;
    private final ClientDatabaseRepository clientDatabaseRepository;
    private final TenantSecretService secretService;
    private final SecureRandom random = new SecureRandom();

    private final String adminUrl;
    private final String adminUsername;
    private final String adminPassword;
    private final String tenantHost;
    private final int tenantPort;

    public TenantProvisioningService(
            ClientRepository clientRepository,
            ClientDatabaseRepository clientDatabaseRepository,
            TenantSecretService secretService,
            @Value("${app.tenant-provisioning.admin-url}") String adminUrl,
            @Value("${app.tenant-provisioning.admin-username}") String adminUsername,
            @Value("${app.tenant-provisioning.admin-password}") String adminPassword,
            @Value("${app.tenant-provisioning.host}") String tenantHost,
            @Value("${app.tenant-provisioning.port}") int tenantPort) {
        this.clientRepository = clientRepository;
        this.clientDatabaseRepository = clientDatabaseRepository;
        this.secretService = secretService;
        this.adminUrl = adminUrl;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.tenantHost = tenantHost;
        this.tenantPort = tenantPort;
    }

    /**
     * Idempotent-ish: safe to call again for a client stuck in FAILED (picks
     * up the same schema/username deterministically from the client's code,
     * generates a fresh password, retries CREATE DATABASE/USER with
     * IF NOT EXISTS so a partially-created database from a previous failed
     * attempt doesn't block the retry).
     */
    public ClientDatabase provision(Long clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new EntityNotFoundException("Client not found: " + clientId));

        String schemaName = requireSafeIdentifier(client.getCode().toLowerCase());
        String tenantUsername = requireSafeIdentifier("tenant_" + schemaName);
        String tenantPassword = generatePassword();

        ClientDatabase clientDatabase = clientDatabaseRepository.findByClientId(clientId).orElseGet(ClientDatabase::new);
        clientDatabase.setClientId(clientId);
        clientDatabase.setHost(tenantHost);
        clientDatabase.setPort(tenantPort);
        clientDatabase.setSchemaName(schemaName);
        clientDatabase.setUsername(tenantUsername);
        clientDatabase.setEncryptedPassword(secretService.encrypt(tenantPassword));
        clientDatabase.setStatus(ClientDatabaseStatus.PROVISIONING);
        clientDatabaseRepository.save(clientDatabase);

        try {
            createDatabaseAndUser(schemaName, tenantUsername, tenantPassword);
            MigrateResult result = Flyway.configure()
                    .dataSource(clientDatabase.jdbcUrl(), tenantUsername, tenantPassword)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .load()
                    .migrate();
            clientDatabase.setSchemaVersion(result.targetSchemaVersion);
            clientDatabase.setStatus(ClientDatabaseStatus.READY);
        } catch (RuntimeException e) {
            clientDatabase.setStatus(ClientDatabaseStatus.FAILED);
            clientDatabaseRepository.save(clientDatabase);
            throw e;
        }
        clientDatabaseRepository.save(clientDatabase);
        return clientDatabase;
    }

    /**
     * IF NOT EXISTS on both statements - makes a retry after a previous
     * failed attempt (e.g. Flyway migration failed after the database/user
     * were already created) safe to just run again rather than needing
     * manual cleanup first. CREATE USER ... IF NOT EXISTS still needs an
     * explicit ALTER USER to pick up a freshly generated password on retry,
     * since IF NOT EXISTS silently no-ops the CREATE (including the
     * password) when the user already exists.
     */
    private void createDatabaseAndUser(String schemaName, String username, String password) {
        try (Connection connection = DriverManager.getConnection(adminUrl, adminUsername, adminPassword);
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + schemaName + "`");
            statement.executeUpdate("CREATE USER IF NOT EXISTS '" + username + "'@'%' IDENTIFIED BY '" + password + "'");
            statement.executeUpdate("ALTER USER '" + username + "'@'%' IDENTIFIED BY '" + password + "'");
            statement.executeUpdate("GRANT ALL PRIVILEGES ON `" + schemaName + "`.* TO '" + username + "'@'%'");
            statement.executeUpdate("FLUSH PRIVILEGES");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to provision database/user for schema " + schemaName, e);
        }
    }

    private String requireSafeIdentifier(String value) {
        if (!SAFE_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalStateException(
                    "Unsafe identifier derived from client code: '" + value + "' - expected 2-40 characters, letters/numbers/underscore only.");
        }
        return value;
    }

    private String generatePassword() {
        byte[] bytes = new byte[GENERATED_PASSWORD_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
