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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * clientId -> HikariDataSource, lazily built and bounded (see the
 * "Database-per-Client Architecture" plan's Dynamic Database Routing
 * design). A Caffeine cache, same library ClientLicenseService's cache
 * already uses (see CacheConfig) - capped at app.tenant-registry.max-pools
 * (default 150), idle-evicted after app.tenant-registry.idle-eviction-minutes
 * (default 30) so an inactive tenant doesn't hold connections forever. Each
 * pool is deliberately small (app.tenant-registry.pool-max-size, default 5;
 * minimumIdle is always 0 - an idle tenant holds zero physical connections)
 * and fail-fast (app.tenant-registry.connection-timeout-ms, default 5000 -
 * one unreachable tenant DB never blocks others). All four tunable without a
 * redeploy as tenant volume grows - see the properties file's own comment
 * for the resource-ceiling math (MySQL's default max_connections=151 won't
 * survive many concurrently-saturated tenant pools at the defaults above),
 * which raising these numbers alone doesn't fix - see client_database.host/
 * port sharding instead once that ceiling is actually being approached.
 */
@Service
public class TenantDataSourceRegistry {

    /** Matches the row every deployment seeds at V87 - see LoginService's own copy of this constant for the same reasoning. */
    private static final String DEFAULT_CLIENT_CODE = "DEFAULT";

    private final ClientDatabaseRepository clientDatabaseRepository;
    private final ClientRepository clientRepository;
    private final TenantSecretService secretService;
    private final int poolMaxSize;
    private final int connectionTimeoutMs;
    private final Cache<Long, HikariDataSource> pools;

    public TenantDataSourceRegistry(
            ClientDatabaseRepository clientDatabaseRepository,
            ClientRepository clientRepository,
            TenantSecretService secretService,
            @Value("${app.tenant-registry.max-pools:150}") int maxPools,
            @Value("${app.tenant-registry.idle-eviction-minutes:30}") long idleEvictionMinutes,
            @Value("${app.tenant-registry.pool-max-size:5}") int poolMaxSize,
            @Value("${app.tenant-registry.connection-timeout-ms:5000}") int connectionTimeoutMs) {
        this.clientDatabaseRepository = clientDatabaseRepository;
        this.clientRepository = clientRepository;
        this.secretService = secretService;
        this.poolMaxSize = poolMaxSize;
        this.connectionTimeoutMs = connectionTimeoutMs;
        this.pools = Caffeine.newBuilder()
                .maximumSize(maxPools)
                .expireAfterAccess(Duration.ofMinutes(idleEvictionMinutes))
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
        config.setMaximumPoolSize(poolMaxSize);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(connectionTimeoutMs);
        config.setPoolName("tenant-" + clientId);
        return new HikariDataSource(config);
    }
}
