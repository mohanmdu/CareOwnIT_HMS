package com.pms.config;

import com.pms.tenant.entity.ClientDatabase;
import com.pms.tenant.entity.ClientDatabaseStatus;
import com.pms.tenant.repository.ClientDatabaseRepository;
import com.pms.tenant.service.TenantSecretService;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Deploy-time migration sweep (see the "Database-per-Client Architecture"
 * plan's Flyway Strategy): applies the existing ~90+ tenant-schema
 * migrations (classpath:db/migration - unchanged, untouched) against every
 * READY tenant database, bounded-parallelism so one slow/unreachable
 * tenant never serializes behind the others. Boot's autoconfigured Flyway
 * bean targets the master database only (see application.properties'
 * spring.flyway.locations) - per-tenant migrations are this programmatic
 * Flyway.configure()...migrate() call, run once per READY row.
 *
 * One tenant's migration failing (DB unreachable, a bad script, ...) is
 * logged and skipped, NOT escalated to ClientDatabaseStatus.FAILED - that
 * would take a previously-working tenant offline (TenantDataSourceRegistry
 * never routes to a non-READY row) over what might be a transient hiccup.
 * A skipped tenant simply stays on its old schema_version until the next
 * successful sweep - see the plan's Risks section on migration-sweep
 * failures. New clients get their full migration set synchronously at
 * provisioning time instead (see TenantProvisioningService) - this sweep
 * exists for "a new migration script shipped, roll it out to every
 * existing tenant" on the next deploy, not for onboarding.
 */
@Component
@Order(0)
public class TenantFlywayRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TenantFlywayRunner.class);
    private static final int SWEEP_CONCURRENCY = 20;

    private final ClientDatabaseRepository clientDatabaseRepository;
    private final TenantSecretService secretService;

    public TenantFlywayRunner(ClientDatabaseRepository clientDatabaseRepository, TenantSecretService secretService) {
        this.clientDatabaseRepository = clientDatabaseRepository;
        this.secretService = secretService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<ClientDatabase> readyTenants = clientDatabaseRepository.findAllByStatus(ClientDatabaseStatus.READY);
        if (readyTenants.isEmpty()) {
            return;
        }
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(SWEEP_CONCURRENCY, readyTenants.size()));
        try {
            CompletableFuture<?>[] futures = readyTenants.stream()
                    .map(db -> CompletableFuture.runAsync(() -> migrateOne(db), pool))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(futures).join();
        } finally {
            pool.shutdown();
        }
    }

    private void migrateOne(ClientDatabase db) {
        try {
            String password = secretService.decrypt(db.getEncryptedPassword());
            MigrateResult result = Flyway.configure()
                    .dataSource(db.jdbcUrl(), db.getUsername(), password)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .load()
                    .migrate();
            if (result.migrationsExecuted > 0) {
                db.setSchemaVersion(result.targetSchemaVersion);
                clientDatabaseRepository.save(db);
                log.info(
                        "Migrated tenant {} ({}) to schema version {} ({} script(s) applied)",
                        db.getClientId(),
                        db.getSchemaName(),
                        result.targetSchemaVersion,
                        result.migrationsExecuted);
            }
        } catch (Exception e) {
            log.error(
                    "Migration sweep failed for tenant {} ({}) - staying on schema version {}, will retry on next sweep",
                    db.getClientId(),
                    db.getSchemaName(),
                    db.getSchemaVersion(),
                    e);
        }
    }
}
