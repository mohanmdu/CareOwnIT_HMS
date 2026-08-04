package com.pms.common;

import com.pms.tenant.TenantContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Local-disk file storage (see app.upload-dir in application.properties),
 * shared by every upload feature in this codebase. Split into two physically
 * separate roots:
 *
 * <p><b>Public</b> ({@link #store}) - consultant photos, CMS images, the
 * clinic logo/favicon. Meant to be visible to anyone with the URL (rendered
 * via plain {@code <img src>} on both hms-web and the public hms-website, so
 * gating them behind auth would just make every one of those images silently
 * fail to load - see ModulePathMappings' own note on /uploads/). Still
 * tenant-scoped in its storage path for hygiene, so different clients'
 * uploads never collide or intermix on disk even though the URLs remain
 * unauthenticated.
 *
 * <p><b>Private</b> ({@link #storePrivate}/{@link #storePrivateDocument}) -
 * patient photos, admission photos, patient report documents. These carry
 * PHI or PHI-adjacent content and are never reachable through the public
 * /uploads/** static handler at all - see PrivateFileController, the only
 * thing that can ever read them back, and which resolves the storage
 * location from the REQUESTER's own TenantContext rather than anything in
 * the URL (see its own doc comment for why that's the actual security
 * boundary here, not just the module/auth gate).
 */
@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> ALLOWED_DOCUMENT_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "application/pdf");

    /** The only categories PrivateFileController will ever serve - anything else is refused, both at write time here and at read time there. */
    public static final Set<String> PRIVATE_CATEGORIES = Set.of("patients", "patient-reports", "admissions");

    /** Matches exactly what storeAs() generates (UUID + short extension) - the filename come back at read time is user input (a URL path segment), so this is what stands between it and path traversal. */
    private static final Pattern SAFE_FILENAME =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.[a-zA-Z0-9]{2,5}$");

    private final Path publicRoot;
    private final Path privateRoot;

    public FileStorageService(@Value("${app.upload-dir}") String uploadDir) {
        Path root = Path.of(uploadDir).toAbsolutePath().normalize();
        this.publicRoot = root.resolve("public");
        this.privateRoot = root.resolve("private");
    }

    /** Public, unauthenticated storage (consultant photos, CMS images, clinic branding) - tenant-scoped on disk for hygiene only, not for access control. Returns its "/uploads/..." URL. */
    public String store(MultipartFile file, String category) {
        requireNotPrivateCategory(category);
        String filename = storeAs(publicRoot.resolve(currentTenantId()).resolve(category), file, imageExtension(file));
        return "/uploads/" + currentTenantId() + "/" + category + "/" + filename;
    }

    /** Private, authenticated storage for images (patient/admission photos). Returns an opaque "/api/files/..." reference with no clientId in it - see the class doc comment. */
    public String storePrivate(MultipartFile file, String category) {
        requirePrivateCategory(category);
        String filename = storeAs(privateRoot.resolve(currentTenantId()).resolve(category), file, imageExtension(file));
        return "/api/files/" + category + "/" + filename;
    }

    /** Same as storePrivate(), but also allows PDF - for the Patient Reports upload flow, which isn't limited to images. */
    public String storePrivateDocument(MultipartFile file, String category) {
        requirePrivateCategory(category);
        String filename = storeAs(privateRoot.resolve(currentTenantId()).resolve(category), file, documentExtension(file));
        return "/api/files/" + category + "/" + filename;
    }

    /**
     * Resolves a private file's on-disk location for the CURRENT REQUESTER's
     * own tenant - see PrivateFileController. If category/filename belongs to
     * a different tenant than the caller, this simply points at a path that
     * doesn't exist (the file physically lives under a different tenant's
     * subdirectory) rather than ever resolving to it.
     */
    public Path resolvePrivateFile(String category, String filename) {
        requirePrivateCategory(category);
        requireSafeFilename(filename);
        return privateRoot.resolve(currentTenantId()).resolve(category).resolve(filename);
    }

    /** Removes a file previously returned by store()/storePrivate()/storePrivateDocument(). No-op if it's already gone or the path shape isn't recognized. */
    public void delete(String relativePath) {
        if (relativePath == null) {
            return;
        }
        if (relativePath.startsWith("/uploads/")) {
            deleteWithin(publicRoot, relativePath.substring("/uploads/".length()));
        } else if (relativePath.startsWith("/api/files/")) {
            String categoryAndFilename = relativePath.substring("/api/files/".length());
            deleteWithin(privateRoot.resolve(currentTenantId()), categoryAndFilename);
        }
    }

    private void deleteWithin(Path root, String relative) {
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Refusing to delete a path outside its storage root");
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete stored file", e);
        }
    }

    private String storeAs(Path directory, MultipartFile file, String extension) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        try {
            Files.createDirectories(directory);
            String filename = UUID.randomUUID() + extension;
            file.transferTo(directory.resolve(filename));
            return filename;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store uploaded file", e);
        }
    }

    private String imageExtension(MultipartFile file) {
        if (!ALLOWED_IMAGE_CONTENT_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Only JPEG, PNG or WEBP images are allowed");
        }
        return switch (file.getContentType()) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    private String documentExtension(MultipartFile file) {
        if (!ALLOWED_DOCUMENT_CONTENT_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Only JPEG, PNG or PDF files are allowed");
        }
        return switch (file.getContentType()) {
            case "image/png" -> ".png";
            case "application/pdf" -> ".pdf";
            default -> ".jpg";
        };
    }

    private void requirePrivateCategory(String category) {
        if (!PRIVATE_CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("Unknown private file category: " + category);
        }
    }

    private void requireNotPrivateCategory(String category) {
        if (PRIVATE_CATEGORIES.contains(category)) {
            throw new IllegalArgumentException(category + " must be stored via storePrivate()/storePrivateDocument(), not store()");
        }
    }

    private void requireSafeFilename(String filename) {
        if (filename == null || !SAFE_FILENAME.matcher(filename).matches()) {
            throw new IllegalArgumentException("Invalid filename");
        }
    }

    /** Every caller here runs inside an authenticated request (uploads/reads/deletes are always behind a module+auth gate), so TenantContext is always set - see JwtAuthenticationFilter. */
    private String currentTenantId() {
        Long clientId = TenantContext.get();
        if (clientId == null) {
            throw new IllegalStateException("No tenant context - cannot resolve a per-client storage location.");
        }
        return String.valueOf(clientId);
    }
}
