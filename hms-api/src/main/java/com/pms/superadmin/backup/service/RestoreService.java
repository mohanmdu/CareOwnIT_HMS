package com.pms.superadmin.backup.service;

import com.pms.common.EntityNotFoundException;
import com.pms.tenant.entity.BackupOperation;
import com.pms.tenant.entity.ClientBackup;
import com.pms.tenant.entity.ClientBackupAuditLog;
import com.pms.tenant.entity.ClientBackupStatus;
import com.pms.tenant.repository.ClientBackupAuditLogRepository;
import com.pms.tenant.repository.ClientBackupRepository;
import com.pms.tenant.entity.Client;
import com.pms.tenant.entity.ClientDatabase;
import com.pms.tenant.entity.ClientDatabaseStatus;
import com.pms.tenant.repository.ClientDatabaseRepository;
import com.pms.tenant.repository.ClientRepository;
import com.pms.tenant.service.TenantSecretService;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.zip.GZIPInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Restores one client's tenant database from its latest verified backup.
 * Deliberately depends on BackupService (not just its sibling storage/
 * encryption collaborators) for one specific reason: every restore first
 * takes a fresh backup of whatever's in the database RIGHT NOW, before
 * touching it - restoring the wrong backup, or restoring at the wrong time,
 * is itself then undoable the same way any other backup is.
 *
 * Unlike a backup (which runs at low-traffic hours in the background),
 * restoring a live schema is inherently disruptive to that one tenant for
 * its duration - this is a maintenance action, not a hot-path-safe one, and
 * the confirmation flow (see BackupController) makes that explicit rather
 * than implying it's as safe/invisible as a nightly backup.
 */
@Service
public class RestoreService {

    private static final Logger LOG = LoggerFactory.getLogger(RestoreService.class);

    private final ClientRepository clientRepository;
    private final ClientDatabaseRepository clientDatabaseRepository;
    private final ClientBackupRepository clientBackupRepository;
    private final ClientBackupAuditLogRepository auditLogRepository;
    private final TenantSecretService tenantSecretService;
    private final BackupEncryptionService encryptionService;
    private final BackupStorageService storageService;
    private final BackupAlertService alertService;
    private final BackupService backupService;
    private final BackupProperties props;

    public RestoreService(
            ClientRepository clientRepository,
            ClientDatabaseRepository clientDatabaseRepository,
            ClientBackupRepository clientBackupRepository,
            ClientBackupAuditLogRepository auditLogRepository,
            TenantSecretService tenantSecretService,
            BackupEncryptionService encryptionService,
            BackupStorageService storageService,
            BackupAlertService alertService,
            BackupService backupService,
            BackupProperties props) {
        this.clientRepository = clientRepository;
        this.clientDatabaseRepository = clientDatabaseRepository;
        this.clientBackupRepository = clientBackupRepository;
        this.auditLogRepository = auditLogRepository;
        this.tenantSecretService = tenantSecretService;
        this.encryptionService = encryptionService;
        this.storageService = storageService;
        this.alertService = alertService;
        this.backupService = backupService;
        this.props = props;
    }

    /**
     * confirmClientCode must exactly match the target client's own code -
     * the "prevent accidental restoration to the wrong client's database"
     * requirement. A typo'd/copy-pasted-from-the-wrong-tab client ID in a
     * URL or request body is caught here even if the Super Admin themself
     * didn't notice the mismatch.
     */
    public void restore(Long clientId, String confirmClientCode, String performedBy) {
        Client client = clientRepository.findById(clientId).orElseThrow(() -> new EntityNotFoundException("Client not found: " + clientId));
        if (!client.getCode().equalsIgnoreCase(confirmClientCode.trim())) {
            throw new IllegalArgumentException(
                    "Confirmation code '" + confirmClientCode + "' does not match client '" + client.getName() + "' (code " + client.getCode()
                            + ") - restore cancelled, nothing was touched.");
        }
        ClientDatabase clientDatabase = clientDatabaseRepository.findByClientIdAndStatus(clientId, ClientDatabaseStatus.READY)
                .orElseThrow(() -> new IllegalStateException("Client " + client.getName() + " has no READY database - cannot restore."));
        ClientBackup backup = clientBackupRepository.findById(clientId).orElseThrow(
                () -> new IllegalStateException("No backup exists yet for " + client.getName() + " - nothing to restore."));
        if (backup.getStatus() != ClientBackupStatus.SUCCESS || backup.getLastSuccessStorageKey() == null) {
            throw new IllegalStateException("No successful backup on file for " + client.getName() + " - cannot restore.");
        }

        audit(clientId, BackupOperation.RESTORE_STARTED, performedBy, "restoring " + backup.getLastSuccessStorageKey());
        LOG.warn("RESTORE starting for client {} ({}) by {} - importing {}", client.getName(), client.getCode(), performedBy,
                backup.getLastSuccessStorageKey());

        Path stagingDir;
        Path encryptedFile = null;
        Path dumpFile = null;
        try {
            stagingDir = Files.createDirectories(Path.of(props.getStagingDir()));
            encryptedFile = stagingDir.resolve("restore-" + clientId + "-" + Instant.now().toEpochMilli() + ".sql.gz.enc");
            dumpFile = Path.of(encryptedFile.toString().replace(".enc", ""));

            // Download and verify the TARGET backup FIRST, before anything else
            // touches storage. This order is load-bearing, not arbitrary: the
            // pre-restore safety snapshot below uploads a brand-new backup for
            // this same client and (with the default retention-count of 1)
            // immediately prunes the older object right after - which, if this
            // ran first, would prune away the exact object we're about to
            // restore, out from under us, on every single restore. Downloading
            // it into local staging first means the snapshot's own pruning can
            // no longer touch what we actually need.
            storageService.download(backup.getLastSuccessStorageKey(), encryptedFile);
            encryptionService.decryptFile(encryptedFile, dumpFile);
            backupService.verifyDump(dumpFile);

            // Now safe: whatever's in the target database right now, however
            // wrong, is itself backed up before we overwrite it - a
            // restore-gone-wrong is then just another restore away from undone.
            backupService.runBackupForClient(clientId, performedBy + " (automatic pre-restore snapshot)");

            restoreDatabase(clientDatabase, dumpFile);

            audit(clientId, BackupOperation.RESTORE_SUCCESS, performedBy, backup.getLastSuccessStorageKey());
            LOG.warn("RESTORE completed for client {} ({}) by {}", client.getName(), client.getCode(), performedBy);
        } catch (Exception e) {
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            audit(clientId, BackupOperation.RESTORE_FAILED, performedBy, reason);
            alertService.alertRestoreFailed(client.getName(), reason);
            throw e instanceof RuntimeException re ? re : new IllegalStateException("Restore failed for " + client.getName(), e);
        } finally {
            backupService.deleteQuietly(encryptedFile);
            backupService.deleteQuietly(dumpFile);
        }
    }

    private void restoreDatabase(ClientDatabase clientDatabase, Path dumpFile) {
        String password = tenantSecretService.decrypt(clientDatabase.getEncryptedPassword());
        Path optionsFile = backupService.writeOptionsFile(clientDatabase, password);
        try {
            ProcessBuilder builder =
                    new ProcessBuilder(props.getMysqlPath(), "--defaults-extra-file=" + optionsFile, clientDatabase.getSchemaName());
            Process process = builder.start();
            try (var gzipIn = new GZIPInputStream(Files.newInputStream(dumpFile));
                    OutputStream mysqlIn = process.getOutputStream()) {
                gzipIn.transferTo(mysqlIn);
            }
            String stderr = new String(process.getErrorStream().readAllBytes());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("mysql restore exited " + exitCode + " for schema " + clientDatabase.getSchemaName() + ": " + stderr);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to run mysql restore for schema " + clientDatabase.getSchemaName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("mysql restore was interrupted for schema " + clientDatabase.getSchemaName(), e);
        } finally {
            backupService.deleteQuietly(optionsFile);
        }
    }

    private void audit(Long clientId, BackupOperation operation, String performedBy, String detail) {
        auditLogRepository.save(new ClientBackupAuditLog(clientId, operation, performedBy, detail));
    }
}
