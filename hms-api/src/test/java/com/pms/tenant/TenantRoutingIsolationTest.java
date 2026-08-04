package com.pms.tenant;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pms.masters.repository.GeneralUserRepository;
import com.pms.tenant.repository.ClientRepository;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The automated version of the manual check that caught Phase A's two real
 * cross-tenant leaks this session (see GeneralUserService/RoleService) -
 * see the "Database-per-Client Architecture" plan's Verification section:
 * "a deliberate 'two tenants, two different client_database rows, assert
 * zero cross-tenant query results' integration test."
 *
 * Runs against the real local databases this session already provisioned
 * (DEFAULT/Navjeevan, CLIENTA/clienta, CLIENTB/clientb, CLIENTC/clientc) -
 * not mocks or Testcontainers, since the whole point is proving
 * TenantRoutingDataSource really does route to physically separate
 * databases, which a mock can't demonstrate.
 */
@SpringBootTest
class TenantRoutingIsolationTest {

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private GeneralUserRepository generalUserRepository;

    /** clientCode -> a username that exists ONLY in that client's own database (see this session's bootstrap calls). */
    private static final Map<String, String> KNOWN_UNIQUE_USERNAME =
            Map.of("DEFAULT", "kvr", "CLIENTA", "clientaadmin", "CLIENTB", "clientbadmin", "CLIENTC", "clientcadmin");

    @Test
    void eachTenantSeesOnlyItsOwnUser() {
        for (Map.Entry<String, String> owner : KNOWN_UNIQUE_USERNAME.entrySet()) {
            Long clientId = resolveClientId(owner.getKey());

            TenantContext.runAs(clientId, () -> {
                assertTrue(
                        generalUserRepository.findByUsernameIgnoreCase(owner.getValue()).isPresent(),
                        "Expected '" + owner.getValue() + "' to exist in " + owner.getKey() + "'s own database");

                for (Map.Entry<String, String> other : KNOWN_UNIQUE_USERNAME.entrySet()) {
                    if (other.getKey().equals(owner.getKey())) {
                        continue;
                    }
                    assertTrue(
                            generalUserRepository.findByUsernameIgnoreCase(other.getValue()).isEmpty(),
                            "Cross-tenant leak: " + owner.getKey() + "'s database returned " + other.getKey() + "'s user '"
                                    + other.getValue() + "'");
                }
                return null;
            });
        }
    }

    /**
     * The concurrency half of the same guarantee: many threads, each
     * repeatedly switching TenantContext to a different tenant and
     * immediately verifying the routed query only ever sees that tenant's
     * own data - the direct regression test for the exact failure mode
     * the plan's design doc calls out (Tomcat reuses worker threads across
     * unrelated requests, so a leaked ThreadLocal would route one client's
     * later request at another client's database).
     */
    @Test
    void concurrentRequestsAcrossTenantsNeverCrossContaminate() throws InterruptedException {
        List<Map.Entry<String, String>> tenants = List.copyOf(KNOWN_UNIQUE_USERNAME.entrySet());
        Map<String, Long> clientIds = tenants.stream().collect(
                java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> resolveClientId(e.getKey())));

        int threadCount = 16;
        int iterationsPerThread = 25;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch done = new CountDownLatch(threadCount);
        List<String> failures = new CopyOnWriteArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            final int threadIndex = t;
            pool.submit(() -> {
                try {
                    for (int i = 0; i < iterationsPerThread; i++) {
                        final int iteration = i;
                        Map.Entry<String, String> tenant = tenants.get((threadIndex + iteration) % tenants.size());
                        Long clientId = clientIds.get(tenant.getKey());
                        TenantContext.runAs(clientId, () -> {
                            boolean ownUserFound = generalUserRepository.findByUsernameIgnoreCase(tenant.getValue()).isPresent();
                            if (!ownUserFound) {
                                failures.add("thread " + threadIndex + " iter " + iteration + ": " + tenant.getKey()
                                        + "'s own user '" + tenant.getValue() + "' not found while routed to it");
                            }
                            for (Map.Entry<String, String> other : tenants) {
                                if (other.getKey().equals(tenant.getKey())) {
                                    continue;
                                }
                                if (generalUserRepository.findByUsernameIgnoreCase(other.getValue()).isPresent()) {
                                    failures.add("thread " + threadIndex + " iter " + iteration + ": routed to " + tenant.getKey()
                                            + " but saw " + other.getKey() + "'s user '" + other.getValue() + "'");
                                }
                            }
                            return null;
                        });
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        assertTrue(done.await(60, TimeUnit.SECONDS), "Concurrency test did not complete within 60s");
        pool.shutdown();

        assertTrue(failures.isEmpty(), () -> "Cross-tenant contamination detected:\n" + String.join("\n", failures));
    }

    private Long resolveClientId(String code) {
        return clientRepository.findByCodeIgnoreCase(code).orElseThrow(() -> new IllegalStateException("Test setup: client " + code
                + " not found - this test expects it to already be provisioned (see this session's manual bootstrap/provisioning calls)."))
                .getId();
    }
}
