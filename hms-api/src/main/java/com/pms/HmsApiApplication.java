package com.pms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// Excludes Spring Boot's default in-memory-user auto-configuration (the
// "Using generated security password" log line) - this app's real auth
// (com.pms.security) looks GeneralUser up directly, bypassing Spring
// Security's UserDetailsService/AuthenticationManager chain entirely, so
// that generated account is unused dead weight, not a real credential.
//
// Excludes DataSourceAutoConfiguration because this app now defines two
// DataSource beans by hand (MasterJpaConfig, TenantJpaConfig) - see the
// "Database-per-Client Architecture" plan. Registering a second DataSource
// bean anywhere silently stops Boot's autoconfigured one from ever being
// created at all (@ConditionalOnMissingBean matches by type, not name), so
// this exclusion isn't optional once a second DataSource exists.
@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class, DataSourceAutoConfiguration.class})
public class HmsApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(HmsApiApplication.class, args);
	}

}
