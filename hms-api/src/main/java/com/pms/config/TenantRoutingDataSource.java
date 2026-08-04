package com.pms.config;

import com.pms.tenant.TenantContext;
import com.pms.tenant.service.TenantDataSourceRegistry;
import java.util.HashMap;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * Routes every tenant-side connection acquisition to the current
 * TenantContext's client, via TenantDataSourceRegistry's lazy/bounded pool
 * cache - see the "Database-per-Client Architecture" plan's Dynamic
 * Database Routing design (AbstractRoutingDataSource + a per-tenant
 * HikariDataSource registry, evaluated against and preferred over
 * Hibernate's native multi-tenancy SPI for the reasons documented there).
 *
 * determineTargetDataSource() is overridden completely rather than relying
 * on AbstractRoutingDataSource's default map-lookup implementation - the
 * registry, not a fixed resolvedDataSources map, is the real source of
 * truth, since tenants are added at runtime (Super Admin onboarding a new
 * client), not known at startup. targetDataSources is still set to an
 * empty map purely so afterPropertiesSet() (Spring calls this
 * automatically as an InitializingBean) doesn't reject a null map.
 *
 * Falls back to the DEFAULT client whenever no TenantContext is set - this
 * covers Hibernate's own schema-validation connection at EntityManagerFactory
 * bootstrap (main thread, no HTTP request, so no context could have been
 * set) and any request that reaches a tenant repository without going
 * through JwtAuthenticationFilter (currently: the public hms-website API,
 * which carries no JWT/clientId at all). Since every tenant database runs
 * the identical Flyway migration set, DEFAULT's schema is a valid stand-in
 * for "does the entity mapping match the expected shape" - and for actual
 * business queries, DEFAULT is correct today because it's the only
 * database anything other than DEFAULT is registered against. This
 * single-fallback simplification is exactly what the Provisioning/Cutover
 * execution-plan phases replace once the public site and other
 * context-free paths carry their own tenant resolution (e.g. by domain).
 */
public class TenantRoutingDataSource extends AbstractRoutingDataSource {

    private final TenantDataSourceRegistry registry;

    public TenantRoutingDataSource(TenantDataSourceRegistry registry) {
        this.registry = registry;
        setTargetDataSources(new HashMap<>());
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return TenantContext.get();
    }

    @Override
    protected DataSource determineTargetDataSource() {
        Long clientId = TenantContext.get();
        if (clientId == null) {
            clientId = registry.defaultClientId();
        }
        return registry.dataSourceFor(clientId);
    }
}
