package com.pms.superadmin.backup.service;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Offsite, S3-compatible object storage for backup files - works against
 * Backblaze B2, Cloudflare R2, AWS S3 itself, or anything else speaking the
 * S3 API, via a configurable endpoint override (see BackupProperties).
 * Deliberately NOT the same server as the database it's backing up (see the
 * whole point of this feature) - see application.properties' app.backup.s3.*
 * doc comment for why credentials default to blank rather than a working
 * local fallback.
 *
 * Object key scheme: backups/&lt;clientCode&gt;/&lt;yyyyMMdd-HHmmss&gt;.sql.gz.enc -
 * always a NEW object per successful run, never an overwrite of a fixed
 * name. "One rolling backup per client" is retention (prune older objects
 * past app.backup.retention-count), not literal overwrite-in-place - see
 * BackupService.promoteAndPrune(). This is what lets a failed upload never
 * put the previous good backup at risk: there's nothing to overwrite, and
 * nothing gets pruned until the new object is confirmed present.
 */
@Service
public class BackupStorageService {

    private static final DateTimeFormatter KEY_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final BackupProperties props;
    private final S3Client s3Client;

    public BackupStorageService(BackupProperties props) {
        this.props = props;
        this.s3Client = props.isStorageConfigured() ? buildClient(props) : null;
    }

    private static S3Client buildClient(BackupProperties props) {
        return S3Client.builder()
                .endpointOverride(URI.create(
                        props.getS3Endpoint().startsWith("http") ? props.getS3Endpoint() : "https://" + props.getS3Endpoint()))
                .region(Region.of(props.getS3Region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getS3AccessKey(), props.getS3SecretKey())))
                // Path-style addressing (bucket.endpoint/key vs endpoint/bucket/key) is
                // what actually works reliably against non-AWS S3-compatible providers
                // like B2/R2 - virtual-hosted-style DNS resolution assumes AWS's own
                // wildcard DNS setup, which these providers don't replicate.
                .forcePathStyle(true)
                .build();
    }

    private void requireConfigured() {
        if (s3Client == null) {
            throw new IllegalStateException(
                    "Backup storage is not configured - set app.backup.s3.endpoint/bucket/access-key/secret-key (BACKUP_S3_* env vars) first.");
        }
    }

    public String buildObjectKey(String clientCode, Instant timestamp) {
        return "backups/" + clientCode.toLowerCase() + "/" + KEY_TIMESTAMP_FORMAT.format(timestamp) + ".sql.gz.enc";
    }

    public void upload(Path localFile, String key) {
        requireConfigured();
        s3Client.putObject(PutObjectRequest.builder().bucket(props.getS3Bucket()).key(key).build(), RequestBody.fromFile(localFile));
    }

    public void download(String key, Path destination) {
        requireConfigured();
        s3Client.getObject(GetObjectRequest.builder().bucket(props.getS3Bucket()).key(key).build(), destination);
    }

    public boolean exists(String key) {
        requireConfigured();
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(props.getS3Bucket()).key(key).build());
            return true;
        } catch (software.amazon.awssdk.services.s3.model.NoSuchKeyException e) {
            return false;
        }
    }

    public long sizeOf(String key) {
        requireConfigured();
        HeadObjectResponse head = s3Client.headObject(HeadObjectRequest.builder().bucket(props.getS3Bucket()).key(key).build());
        return head.contentLength();
    }

    /** Every object under this client's prefix, newest first - used by BackupService's retention pruning. */
    public List<String> listKeysForClientNewestFirst(String clientCode) {
        requireConfigured();
        String prefix = "backups/" + clientCode.toLowerCase() + "/";
        return s3Client.listObjectsV2(ListObjectsV2Request.builder().bucket(props.getS3Bucket()).prefix(prefix).build())
                .contents()
                .stream()
                .sorted(Comparator.comparing(S3Object::key).reversed())
                .map(S3Object::key)
                .toList();
    }

    public void delete(String key) {
        requireConfigured();
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(props.getS3Bucket()).key(key).build());
    }
}
