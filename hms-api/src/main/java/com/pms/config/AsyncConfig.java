package com.pms.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Enables @Async (see com.pms.contact.ContactMailService) - not on by default in Spring Boot. */
@Configuration
@EnableAsync
public class AsyncConfig {}
