package com.pms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// Excludes Spring Boot's default in-memory-user auto-configuration (the
// "Using generated security password" log line) - this app's real auth
// (com.pms.security) looks GeneralUser up directly, bypassing Spring
// Security's UserDetailsService/AuthenticationManager chain entirely, so
// that generated account is unused dead weight, not a real credential.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class HmsApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(HmsApiApplication.class, args);
	}

}
