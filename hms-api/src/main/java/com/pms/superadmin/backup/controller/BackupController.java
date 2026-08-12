package com.pms.superadmin.backup.controller;

import com.pms.superadmin.backup.dto.ClientBackupAuditLogDto;
import com.pms.superadmin.backup.dto.ClientBackupDto;
import com.pms.superadmin.backup.dto.RestoreRequest;
import com.pms.superadmin.backup.service.BackupService;
import com.pms.superadmin.backup.service.RestoreService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reachable only with a SUPER_ADMIN-authority JWT - see
 * ModuleAuthorizationManager's SUPER_ADMIN_PREFIX branch, same boundary
 * every other /api/super-admin/** controller relies on. Never exposes
 * database or object-storage credentials in any response here - only
 * status/size/timestamp metadata (see ClientBackupDto).
 */
@RestController
@RequestMapping("/api/super-admin/backups")
public class BackupController {

    private final BackupService backupService;
    private final RestoreService restoreService;

    public BackupController(BackupService backupService, RestoreService restoreService) {
        this.backupService = backupService;
        this.restoreService = restoreService;
    }

    @GetMapping
    public List<ClientBackupDto> list() {
        return backupService.listAll();
    }

    @GetMapping("/{clientId}/audit-log")
    public List<ClientBackupAuditLogDto> auditLog(@PathVariable Long clientId) {
        return backupService.auditLogFor(clientId);
    }

    /**
     * Synchronous and potentially slow for a large database - same
     * "correctness over a misleadingly-instant response" tradeoff
     * ClientController.provisionDatabase() already makes for the
     * comparably slow full-migration-run action.
     */
    @PostMapping("/{clientId}/run")
    public void runNow(@PathVariable Long clientId) {
        backupService.runBackupForClient(clientId, BackupService.currentPerformer());
    }

    /** Decrypted server-side and streamed - see BackupService.downloadDecrypted()'s own doc comment on why. */
    @GetMapping("/{clientId}/download")
    public void download(@PathVariable Long clientId, HttpServletResponse response) throws IOException {
        Path file = backupService.downloadDecrypted(clientId);
        try {
            response.setContentType("application/gzip");
            response.setHeader("Content-Disposition", "attachment; filename=\"client-" + clientId + "-backup.sql.gz\"");
            response.setContentLengthLong(Files.size(file));
            Files.copy(file, response.getOutputStream());
            response.getOutputStream().flush();
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /**
     * Requires the client's own code in the request body as an explicit
     * confirmation - see RestoreService.restore()'s own doc comment on why
     * this exists (preventing a wrong-client accident, not just a UI
     * "are you sure?" dialog that a mis-clicked confirm button bypasses).
     */
    @PostMapping("/{clientId}/restore")
    public void restore(@PathVariable Long clientId, @Valid @RequestBody RestoreRequest request) {
        restoreService.restore(clientId, request.confirmClientCode(), BackupService.currentPerformer());
    }
}
