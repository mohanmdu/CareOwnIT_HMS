package com.pms.config;

import com.pms.tenant.service.TenantDataSourceRegistry;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.ManagedClassNameFilter;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypesScanner;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The tenant database - now dynamically routed per request (see
 * TenantRoutingDataSource/TenantContext/TenantDataSourceRegistry), one
 * physical database per Client, resolved from the master DB's
 * client_database table instead of the earlier INTERIM single hardcoded
 * DataSource. See the "Database-per-Client Architecture" plan's Dynamic
 * Database Routing design.
 *
 * @Primary on the EntityManagerFactory/TransactionManager (not the
 * DataSource itself) keeps every existing unqualified @Transactional across
 * the ~15 non-tenant modules (registration, pharmacy, lab, ...) pointed
 * here unchanged - only com.pms.tenant.repository is excluded, via
 * MasterJpaConfig's own explicit basePackages instead.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.pms",
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.pms\\.tenant\\.repository\\..*"),
        entityManagerFactoryRef = "tenantEntityManagerFactory",
        transactionManagerRef = "tenantTransactionManager")
public class TenantJpaConfig {

    @Bean
    public DataSource tenantDataSource(TenantDataSourceRegistry registry) {
        return new TenantRoutingDataSource(registry);
    }

    /** Every entity except com.pms.tenant.entity (Client/ClientDatabase/SuperAdminUser - master's own). */
    @Bean
    public PersistenceManagedTypes tenantManagedTypes(ResourceLoader resourceLoader) {
        ManagedClassNameFilter excludeMasterEntities = className -> !className.startsWith("com.pms.tenant.entity");
        return new PersistenceManagedTypesScanner(resourceLoader, excludeMasterEntities).scan("com.pms");
    }

    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean tenantEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("tenantDataSource") DataSource tenantDataSource,
            PersistenceManagedTypes tenantManagedTypes) {
        return builder.dataSource(tenantDataSource).managedTypes(tenantManagedTypes).persistenceUnit("tenant").build();
    }

    @Bean
    @Primary
    public PlatformTransactionManager tenantTransactionManager(
            @Qualifier("tenantEntityManagerFactory") LocalContainerEntityManagerFactoryBean tenantEmf) {
        return new JpaTransactionManager(tenantEmf.getObject());
    }
}
