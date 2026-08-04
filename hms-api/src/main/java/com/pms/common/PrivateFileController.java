package com.pms.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Streams the PHI/PHI-adjacent uploaded files FileStorageService keeps out
 * of the public /uploads/** static handler (patient photos, admission
 * photos, patient report documents - see FileStorageService.PRIVATE_CATEGORIES).
 *
 * The real security boundary here isn't the module/auth gate alone (see
 * ModulePathMappings for the per-category module mapping) - it's that
 * {@code category}/{@code filename} from the URL are only ever resolved
 * against the REQUESTER's own TenantContext (set by JwtAuthenticationFilter
 * from their verified JWT), never against anything the client could
 * influence. A filename that belongs to a different tenant's upload simply
 * doesn't exist under the caller's own resolved directory - cross-tenant
 * access is impossible by construction here, not by a check that could be
 * missed or bypassed.
 */
@RestController
@RequestMapping("/api/files")
public class PrivateFileController {

    private final FileStorageService fileStorageService;

    public PrivateFileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/{category}/{filename}")
    public ResponseEntity<Resource> get(@PathVariable String category, @PathVariable String filename) throws IOException {
        Path file = fileStorageService.resolvePrivateFile(category, filename);
        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        String probed = Files.probeContentType(file);
        MediaType contentType = probed != null ? MediaType.parseMediaType(probed) : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok().contentType(contentType).body(new FileSystemResource(file));
    }
}
