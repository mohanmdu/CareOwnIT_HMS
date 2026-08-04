package com.pms.tenant.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.pms.common.EntityNotFoundException;
import com.pms.tenant.entity.ClientDatabase;
import com.pms.tenant.entity.ClientDatabaseStatus;
import com.pms.tenant.repository.ClientDatabaseRepository;
import com.pms.tenant.repository.ClientRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import javax.sql.DataSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * clientId -> HikariDataSource, lazily built and bounded (see the
 * "Database-per-Client Architecture" plan's Dynamic Database Routing
 * design). A Caffeine cache, same library ClientLicenseService's cache
 * already uses (see CacheConfig) - capped at 150 pools, idle-evicted after
 * 30 minutes so an inactive tenant doesn't hold connections forever. Each
 * pool is deliberately small (maximumPoolSize=5, minimumIdle=0 - an idle
 * tenant holds zero physical connections) and fail-fast
 * (connectionTimeout=5s - one unreachable tenant DB never blocks others).
 *
 * Resource ceiling note (see the plan's Risks section): MySQL's default
 * max_connections=151 will not survive more than ~20-25 concurrently-
 * saturated tenant pools at this sizing - fine for today's single
 * registered tenant, needs revisiting before real multi-tenant volume.
 */
@Service
public class TenantDataSourceRegistry {

    private static final int MAX_POOLS = 150;
    private static final Duration IDLE_EVICTION = Duration.ofMinutes(30);
    private static final int POOL_MAX_SIZE = 5;
    private static final int CONNECTION_TIMEOUT_MS = 5000;

    /** Matches the row every deployment seeds at V87 - see LoginService's own copy of this constant for the same reasoning. */
    private static final String DEFAULT_CLIENT_CODE = "DEFAULT";

    private final ClientDatabaseRepository clientDatabaseRepository;
    private final ClientRepository clientRepository;
    private final TenantSecretService secretService;
    private final Cache<Long, HikariDataSource> pools;

    public TenantDataSourceRegistry(
            ClientDatabaseRepository clientDatabaseRepository, ClientRepository clientRepository, TenantSecretService secretService) {
        this.clientDatabaseRepository = clientDatabaseRepository;
        this.clientRepository = clientRepository;
        this.secretService = secretService;
        this.pools = Caffeine.newBuilder()
                .maximumSize(MAX_POOLS)
                .expireAfterAccess(IDLE_EVICTION)
                .<Long, HikariDataSource>evictionListener((clientId, pool, cause) -> {
                    if (pool != null) {
                        pool.close();
                    }
                })
                .build();
    }

    public DataSource dataSourceFor(Long clientId) {
        return pools.get(clientId, this::buildPool);
    }

    /** The tenant DataSource routing falls back to whenever no TenantContext is set - Hibernate/Flyway startup validation, and any request that never went through JwtAuthenticationFilter (e.g. the public site). See TenantRoutingDataSource. */
    @Transactional(readOnly = true, transactionManager = "masterTransactionManager")
    public Long defaultClientId() {
        return clientRepository.findByCodeIgnoreCase(DEFAULT_CLIENT_CODE)
                .orElseThrow(() -> new IllegalStateException("No '" + DEFAULT_CLIENT_CODE + "' client seeded in the master database."))
                .getId();
    }

    @Transactional(readOnly = true, transactionManager = "masterTransactionManager")
    HikariDataSource buildPool(Long clientId) {
        ClientDatabase clientDatabase = clientDatabaseRepository
                .findByClientIdAndStatus(clientId, ClientDatabaseStatus.READY)
                .orElseThrow(() -> new EntityNotFoundException("No READY database registered for client " + clientId));
        String password = secretService.decrypt(clientDatabase.getEncryptedPassword());
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(clientDatabase.jdbcUrl());
        config.setUsername(clientDatabase.getUsername());
        config.setPassword(password);
        config.setMaximumPoolSize(POOL_MAX_SIZE);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        config.setPoolName("tenant-" + clientId);
        return new HikariDataSource(config);
    }
}
