package com.pms.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jpa.EntityManagerFactoryBuilder;
import org.springframework.core.io.ResourceLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypesScanner;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The master database (Phase B - see the "Database-per-Client Architecture"
 * plan) - client/subscription/license/connection registry only, never
 * clinical/business data, never tenant-routed. Backed by the standard
 * spring.datasource.* properties (DB_URL/DB_USER/DB_PASSWORD env vars,
 * unchanged names - only their meaning shifted from "the one tenant DB" to
 * "the master DB" now that a master DB is a real, permanent concept).
 *
 * Registering this DataSource bean (and TenantJpaConfig's) means Spring
 * Boot's autoconfigured DataSource silently never gets created at all -
 * DataSourceAutoConfiguration is excluded on HmsApiApplication for exactly
 * this reason. HibernateJpaAutoConfiguration is deliberately NOT excluded -
 * it still supplies the EntityManagerFactoryBuilder/JpaVendorAdapter beans
 * both this class and TenantJpaConfig depend on; its own default
 * entityManagerFactory() bean backs off via @ConditionalOnMissingBean once
 * two manual EntityManagerFactories already exist.
 *
 * Deliberately NOT @Primary on the EntityManagerFactory/TransactionManager
 * (only the DataSource is, so Flyway autoconfiguration binds to master) -
 * see TenantJpaConfig's own doc comment for why tenant holds that instead:
 * every existing unqualified @Transactional across the ~15 non-tenant
 * modules must keep resolving to the tenant DB. Services that live here
 * (ClientService, ClientLicenseService, SuperAdminLoginService, ...) must
 * qualify their own @Transactional(transactionManager = "masterTransactionManager").
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.pms.tenant.repository",
        entityManagerFactoryRef = "masterEntityManagerFactory",
        transactionManagerRef = "masterTransactionManager")
public class MasterJpaConfig {

    @Bean
    @Primary
    public DataSourceProperties masterDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public DataSource masterDataSource(@Qualifier("masterDataSourceProperties") DataSourceProperties props) {
        return props.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    /** Scoped to com.pms.tenant.entity only - Client/ClientStatus/DeploymentType/ClientDatabase/ClientDatabaseStatus/SuperAdminUser. Everything else belongs to TenantJpaConfig's EntityManagerFactory. */
    @Bean
    public PersistenceManagedTypes masterManagedTypes(ResourceLoader resourceLoader) {
        return new PersistenceManagedTypesScanner(resourceLoader).scan("com.pms.tenant.entity");
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean masterEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("masterDataSource") DataSource masterDataSource,
            PersistenceManagedTypes masterManagedTypes) {
        return builder.dataSource(masterDataSource).managedTypes(masterManagedTypes).persistenceUnit("master").build();
    }

    @Bean
    public PlatformTransactionManager masterTransactionManager(
            @Qualifier("masterEntityManagerFactory") LocalContainerEntityManagerFactoryBean masterEmf) {
        return new JpaTransactionManager(masterEmf.getObject());
    }
}
