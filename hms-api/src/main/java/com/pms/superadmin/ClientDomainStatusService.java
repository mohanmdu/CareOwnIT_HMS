package com.pms.superadmin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * On-demand "is this client's public website actually reachable" probe -
 * the same HTTPS check that would otherwise be run by hand with curl after
 * wiring up DNS/nginx/TLS for a client's subdomain (see
 * scripts/provision-client-subdomain.sh), exposed as a status badge instead.
 * Deliberately not persisted anywhere - computed fresh on every call, same
 * "low-frequency, no queueing" style already used for the rest of Super
 * Admin's client-management actions. Same java.net.http.HttpClient idiom as
 * BackupAlertService: synchronous, best-effort, never throws.
 */
@Service
public class ClientDomainStatusService {

    private static final Logger LOG = LoggerFactory.getLogger(ClientDomainStatusService.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public String check(String domain) {
        if (domain == null || domain.isBlank()) {
            return "NOT_SET";
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://" + domain + "/"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() < 400 ? "LIVE" : "UNREACHABLE";
        } catch (Exception e) {
            LOG.debug("Domain status check failed for '{}'", domain, e);
            return "UNREACHABLE";
        }
    }
}
