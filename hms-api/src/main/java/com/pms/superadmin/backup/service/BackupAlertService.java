package com.pms.superadmin.backup.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Push notifications for backup failures, via the same ntfy.sh channel/topic
 * scripts/alert-check.sh already uses for disk/CPU/RAM alerts on the VPS -
 * deliberately not a new channel (email/Slack/etc), so there's exactly one
 * place ops actually watches for "something needs attention", not two.
 * Best-effort: a failed alert send is logged, never allowed to affect
 * whether the backup itself is recorded as failed.
 */
@Service
public class BackupAlertService {

    private static final Logger LOG = LoggerFactory.getLogger(BackupAlertService.class);

    private final String ntfyTopic;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public BackupAlertService(BackupProperties props) {
        this.ntfyTopic = props.getNtfyTopic();
    }

    public void alertBackupFailed(String clientName, String reason) {
        send("Backup failed: " + clientName, reason);
    }

    public void alertRestoreFailed(String clientName, String reason) {
        send("RESTORE failed: " + clientName, reason);
    }

    private void send(String title, String message) {
        if (ntfyTopic.isBlank()) {
            LOG.warn("Backup alert (no ntfy topic configured, logging only) - {}: {}", title, message);
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://ntfy.sh/" + ntfyTopic))
                    .header("Title", "CareOwn Backup Alert: " + title)
                    .header("Priority", "high")
                    .header("Tags", "warning")
                    .POST(BodyPublishers.ofString(message, StandardCharsets.UTF_8))
                    .build();
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            LOG.warn("Failed to send ntfy backup alert for '{}'", title, e);
        }
    }
}
