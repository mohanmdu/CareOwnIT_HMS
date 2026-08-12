package com.pms.superadmin.backup.service;

import com.pms.common.EntityNotFoundException;
import com.pms.superadmin.backup.dto.ClientBackupDto;
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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates one client's daily backup end to end: dump -&gt; verify -&gt;
 * encrypt -&gt; upload -&gt; promote/prune -&gt; record. See the class-level docs on
 * BackupStorageService (storage layout) and BackupEncryptionService
 * (encryption) for the pieces this composes.
 *
 * The one rule every step below is built around: a failed run must NEVER
 * touch ClientBackup.lastSuccess* or delete a previously-uploaded object.
 * Every intermediate step either succeeds and moves the run forward, or
 * throws - and the catch block in runBackupForClient() only ever updates
 * the *failure* fields, never the success ones.
 */
@Service
public class BackupService {

    private static final Logger LOG = LoggerFactory.getLogger(BackupService.class);
    private static final long MIN_PLAUSIBLE_DUMP_BYTES = 200;
    private static final int FAILURE_REASON_MAX_LENGTH = 1000;

    private final ClientRepository clientRepository;
    private final ClientDatabaseRepository clientDatabaseRepository;
    private final ClientBackupRepository clientBackupRepository;
    private final ClientBackupAuditLogRepository auditLogRepository;
    private final TenantSecretService tenantSecretService;
    private final BackupEncryptionService encryptionService;
    private final BackupStorageService storageService;
    private final BackupAlertService alertService;
    private final BackupProperties props;

    public BackupService(
            ClientRepository clientRepository,
            ClientDatabaseRepository clientDatabaseRepository,
            ClientBackupRepository clientBackupRepository,
            ClientBackupAuditLogRepository auditLogRepository,
            TenantSecretService tenantSecretService,
            BackupEncryptionService encryptionService,
            BackupStorageService storageService,
            BackupAlertService alertService,
            BackupProperties props) {
        this.clientRepository = clientRepository;
        this.clientDatabaseRepository = clientDatabaseRepository;
        this.clientBackupRepository = clientBackupRepository;
        this.auditLogRepository = auditLogRepository;
        this.tenantSecretService = tenantSecretService;
        this.encryptionService = encryptionService;
        this.storageService = storageService;
        this.alertService = alertService;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public List<ClientBackupDto> listAll() {
        return clientRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<com.pms.superadmin.backup.dto.ClientBackupAuditLogDto> auditLogFor(Long clientId) {
        return auditLogRepository.findByClientIdOrderByPerformedAtDesc(clientId).stream()
                .map(entry -> new com.pms.superadmin.backup.dto.ClientBackupAuditLogDto(
                        entry.getOperation(), entry.getPerformedBy(), entry.getPerformedAt(), entry.getDetail()))
                .toList();
    }

    /**
     * Downloads and decrypts the client's latest verified backup into a
     * local temp file for BackupController to stream back to the Super
     * Admin - decrypted server-side rather than handing back an encrypted
     * blob the caller has no key for, since Super Admin is already the
     * highest-trust actor in the system. Re-verifies (gzip integrity +
     * CREATE TABLE presence) after decrypting, same as a fresh backup would
     * be - a corrupted/tampered object should never reach a download link
     * silently. Caller is responsible for deleting the returned path once
     * done streaming it.
     */
    public Path downloadDecrypted(Long clientId) {
        Client client = clientRepository.findById(clientId).orElseThrow(() -> new EntityNotFoundException("Client not found: " + clientId));
        ClientBackup backup = clientBackupRepository.findById(clientId)
                .orElseThrow(() -> new IllegalStateException("No backup exists yet for " + client.getName() + "."));
        if (backup.getStatus() != ClientBackupStatus.SUCCESS || backup.getLastSuccessStorageKey() == null) {
            throw new IllegalStateException("No successful backup on file for " + client.getName() + ".");
        }
        try {
            Path stagingDir = Files.createDirectories(Path.of(props.getStagingDir()));
            Path encryptedFile = stagingDir.resolve("download-" + clientId + "-" + Instant.now().toEpochMilli() + ".sql.gz.enc");
            Path dumpFile = Path.of(encryptedFile.toString().replace(".enc", ""));
            try {
                storageService.download(backup.getLastSuccessStorageKey(), encryptedFile);
                encryptionService.decryptFile(encryptedFile, dumpFile);
                verifyDump(dumpFile);
                audit(clientId, BackupOperation.DOWNLOAD, currentPerformer(), backup.getLastSuccessStorageKey());
                return dumpFile;
            } finally {
                deleteQuietly(encryptedFile);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to prepare backup download for " + client.getName(), e);
        }
    }

    /** The scheduled job's entry point - every client gets attempted even if an earlier one fails, so one bad tenant never silently starves backup coverage for the rest. */
    public void runBackupForAllReadyClients() {
        List<ClientDatabase> readyClients = clientDatabaseRepository.findAllByStatus(ClientDatabaseStatus.READY);
        LOG.info("Scheduled backup run starting for {} READY client(s)", readyClients.size());
        for (ClientDatabase clientDatabase : readyClients) {
            try {
                runBackupForClient(clientDatabase.getClientId(), "SYSTEM");
            } catch (RuntimeException e) {
                LOG.error("Scheduled backup failed for client {}", clientDatabase.getClientId(), e);
            }
        }
    }

    /**
     * One client, start to finish. Rethrows on failure (after recording it)
     * so a manual "Run Backup Now" call can surface the error to the Super
     * Admin who triggered it - the bulk scheduled path above catches around
     * each call instead.
     */
    public void runBackupForClient(Long clientId, String performedBy) {
        Client client = clientRepository.findById(clientId).orElseThrow(() -> new EntityNotFoundException("Client not found: " + clientId));
        ClientDatabase clientDatabase = clientDatabaseRepository.findByClientIdAndStatus(clientId, ClientDatabaseStatus.READY)
                .orElseThrow(() -> new IllegalStateException("Client " + client.getName() + " has no READY database - nothing to back up."));

        ClientBackup backup = clientBackupRepository.findById(clientId).orElseGet(() -> {
            ClientBackup fresh = new ClientBackup();
            fresh.setClientId(clientId);
            return fresh;
        });
        backup.setStatus(ClientBackupStatus.IN_PROGRESS);
        backup.setLastAttemptAt(Instant.now());
        clientBackupRepository.save(backup);
        audit(clientId, BackupOperation.BACKUP_STARTED, performedBy, "schema=" + clientDatabase.getSchemaName());

        Path stagingDir = null;
        Path dumpFile = null;
        Path encryptedFile = null;
        try {
            stagingDir = Files.createDirectories(Path.of(props.getStagingDir()));
            dumpFile = stagingDir.resolve(clientDatabase.getSchemaName() + "-" + Instant.now().toEpochMilli() + ".sql.gz");
            encryptedFile = Path.of(dumpFile + ".enc");

            dumpDatabase(clientDatabase, dumpFile);
            verifyDump(dumpFile);
            encryptionService.encryptFile(dumpFile, encryptedFile);
            String checksum = sha256Hex(encryptedFile);

            Instant now = Instant.now();
            String key = storageService.buildObjectKey(client.getCode(), now);
            storageService.upload(encryptedFile, key);

            long uploadedSize = storageService.sizeOf(key);
            long localSize = Files.size(encryptedFile);
            if (!storageService.exists(key) || uploadedSize != localSize) {
                throw new IllegalStateException(
                        "Upload verification failed for " + key + " (local " + localSize + " bytes, remote " + uploadedSize + " bytes)");
            }

            // Only now, with the new backup confirmed present in storage, do we
            // touch anything that could affect the previous good backup.
            backup.setStatus(ClientBackupStatus.SUCCESS);
            backup.setLastSuccessAt(now);
            backup.setLastSuccessStorageKey(key);
            backup.setLastSuccessSizeBytes(uploadedSize);
            backup.setLastSuccessChecksum(checksum);
            clientBackupRepository.save(backup);
            audit(clientId, BackupOperation.BACKUP_SUCCESS, performedBy, key + " (" + uploadedSize + " bytes)");

            // Deliberately its own try/catch, not part of the block above: the
            // backup itself is already safely stored and recorded as SUCCESS by
            // this point. A prune failure (e.g. one delete call errors) is a
            // storage-hygiene issue, not a backup failure - it must never fall
            // into the catch below and overwrite an already-successful run's
            // status to FAILED.
            try {
                pruneOldBackups(client.getCode(), key);
            } catch (RuntimeException e) {
                LOG.warn("Backup for {} succeeded but pruning old backups failed - an extra old object may linger in storage", client.getName(), e);
            }
        } catch (Exception e) {
            String reason = truncate(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            backup.setStatus(ClientBackupStatus.FAILED);
            backup.setLastFailureAt(Instant.now());
            backup.setLastFailureReason(reason);
            // Deliberately no touch of lastSuccess* fields above or below this line.
            clientBackupRepository.save(backup);
            audit(clientId, BackupOperation.BACKUP_FAILED, performedBy, reason);
            alertService.alertBackupFailed(client.getName(), reason);
            throw e instanceof RuntimeException re ? re : new IllegalStateException("Backup failed for " + client.getName(), e);
        } finally {
            deleteQuietly(dumpFile);
            deleteQuietly(encryptedFile);
        }
    }

    /** Deletes every object for this client older than the one just uploaded, keeping only the newest app.backup.retention-count - see BackupStorageService's own doc comment on why this is "prune after the fact", not "overwrite in place". */
    private void pruneOldBackups(String clientCode, String justUploadedKey) {
        List<String> keysNewestFirst = storageService.listKeysForClientNewestFirst(clientCode);
        if (keysNewestFirst.size() <= props.getRetentionCount()) {
            return;
        }
        // Belt-and-suspenders: never prune the object we just confirmed, even
        // if listing returns a surprising order.
        for (String key : keysNewestFirst.subList(props.getRetentionCount(), keysNewestFirst.size())) {
            if (!key.equals(justUploadedKey)) {
                storageService.delete(key);
            }
        }
    }

    private void dumpDatabase(ClientDatabase clientDatabase, Path dumpFile) {
        String password = tenantSecretService.decrypt(clientDatabase.getEncryptedPassword());
        Path optionsFile = writeOptionsFile(clientDatabase, password);
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    props.getMysqldumpPath(),
                    "--defaults-extra-file=" + optionsFile,
                    "--single-transaction",
                    "--routines",
                    "--triggers",
                    "--no-tablespaces",
                    "--quick",
                    // Self-contained for restore: each table is dropped immediately
                    // before being recreated, so RestoreService can import straight
                    // into the existing (not-empty) schema without a separate
                    // "wipe everything first" step of its own.
                    "--add-drop-table",
                    clientDatabase.getSchemaName());
            builder.redirectErrorStream(false);
            Process process = builder.start();
            try (InputStream dumpOut = process.getInputStream();
                    var gzipOut = new GZIPOutputStream(Files.newOutputStream(dumpFile))) {
                dumpOut.transferTo(gzipOut);
            }
            String stderr = new String(process.getErrorStream().readAllBytes());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("mysqldump exited " + exitCode + " for schema " + clientDatabase.getSchemaName() + ": " + stderr);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to run mysqldump for schema " + clientDatabase.getSchemaName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("mysqldump was interrupted for schema " + clientDatabase.getSchemaName(), e);
        } finally {
            deleteQuietly(optionsFile);
        }
    }

    /**
     * A restricted-permission temp file instead of -u/-p command-line flags -
     * mysqldump's password otherwise appears (briefly, but really) in `ps
     * aux` output on the box, which the legacy backup.sh script actually
     * does today. POSIX permissions are best-effort (a Windows dev machine
     * has no such concept; this matters for the real Linux VPS this feature
     * targets).
     */
    /** Package-private (not private) - RestoreService reuses this for the mysql CLI import, same credential-safety rationale as the mysqldump call above. */
    Path writeOptionsFile(ClientDatabase clientDatabase, String password) {
        try {
            Path file = Files.createTempFile("hms-backup-", ".cnf");
            String content = "[client]\nhost=" + clientDatabase.getHost() + "\nport=" + clientDatabase.getPort() + "\nuser="
                    + clientDatabase.getUsername() + "\npassword=" + password + "\n";
            Files.writeString(file, content);
            try {
                Set<PosixFilePermission> ownerOnly = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
                Files.setPosixFilePermissions(file, ownerOnly);
            } catch (UnsupportedOperationException ignored) {
                // Not a POSIX filesystem (e.g. local Windows dev) - nothing to restrict.
            }
            return file;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write temporary MySQL options file", e);
        }
    }

    /**
     * The exact class of check the repo's own vps-mysql-runbook.md documents
     * as the one that would have caught the 2026-08-06 backup bug sooner:
     * don't just trust that mysqldump exited 0 - open the file and confirm
     * it's really there. gzip integrity + real CREATE TABLE content + a
     * non-trivial size floor, all cheap enough to run on every single backup.
     */
    /** Package-private (not private) - RestoreService reuses this same check on a downloaded/decrypted backup before importing it. */
    void verifyDump(Path dumpFile) {
        long size = fileSize(dumpFile);
        if (size < MIN_PLAUSIBLE_DUMP_BYTES) {
            throw new IllegalStateException("Backup file implausibly small (" + size + " bytes) - refusing to treat it as a valid dump.");
        }
        boolean sawCreateTable = false;
        try (GZIPInputStream gzipIn = new GZIPInputStream(Files.newInputStream(dumpFile))) {
            byte[] buffer = new byte[65536];
            StringBuilder tail = new StringBuilder();
            int read;
            while ((read = gzipIn.read(buffer)) != -1) {
                String chunk = new String(buffer, 0, read, java.nio.charset.StandardCharsets.ISO_8859_1);
                if (!sawCreateTable && (tail + chunk).contains("CREATE TABLE")) {
                    sawCreateTable = true;
                }
                // Only need the boundary between chunks, not the whole growing file, in memory.
                tail = new StringBuilder(chunk.length() > 20 ? chunk.substring(chunk.length() - 20) : chunk);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Backup file failed gzip integrity check - it may be truncated or corrupted.", e);
        }
        if (!sawCreateTable) {
            throw new IllegalStateException("Backup file contains no CREATE TABLE statements - refusing to treat an empty/wrong dump as valid.");
        }
    }

    private long fileSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new IllegalStateException("Backup file is missing immediately after mysqldump completed: " + file, e);
        }
    }

    private String sha256Hex(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to checksum backup file " + file, e);
        }
    }

    private void audit(Long clientId, BackupOperation operation, String performedBy, String detail) {
        auditLogRepository.save(new ClientBackupAuditLog(clientId, operation, performedBy, detail));
    }

    /** Package-private (not private) - RestoreService reuses this for its own staging-file cleanup. */
    void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOG.warn("Failed to delete temporary backup file {}", file, e);
        }
    }

    private String truncate(String value) {
        return value.length() <= FAILURE_REASON_MAX_LENGTH ? value : value.substring(0, FAILURE_REASON_MAX_LENGTH);
    }

    /** "system" for the scheduled job (no authenticated user), the Super Admin's own username for a manual trigger/download/restore. */
    public static String currentPerformer() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated() ? authentication.getName() : "SYSTEM";
    }

    private ClientBackupDto toDto(Client client) {
        ClientDatabase clientDatabase = clientDatabaseRepository.findByClientId(client.getId()).orElse(null);
        ClientBackup backup = clientBackupRepository.findById(client.getId()).orElse(null);
        return new ClientBackupDto(
                client.getId(),
                client.getName(),
                client.getCode(),
                clientDatabase != null ? clientDatabase.getSchemaName() : null,
                backup != null ? backup.getStatus() : ClientBackupStatus.NEVER_RUN,
                backup != null ? backup.getLastAttemptAt() : null,
                backup != null ? backup.getLastSuccessAt() : null,
                backup != null ? backup.getLastSuccessSizeBytes() : null,
                backup != null ? backup.getLastFailureAt() : null,
                backup != null ? backup.getLastFailureReason() : null);
    }
}
