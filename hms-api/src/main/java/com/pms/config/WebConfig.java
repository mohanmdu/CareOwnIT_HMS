package com.pms.config;

import com.pms.settings.service.ClinicSettingsService;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.ObjectMapper;

/**
 * Serves uploaded consultant photos (see app.upload-dir) as static files
 * under /uploads/** - the Angular dev server proxy.conf.json forwards that
 * path alongside /api.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String uploadDir;
    private final ClinicSettingsService clinicSettingsService;
    private final ObjectMapper objectMapper;

    public WebConfig(
            @Value("${app.upload-dir}") String uploadDir,
            ClinicSettingsService clinicSettingsService,
            ObjectMapper objectMapper) {
        this.uploadDir = uploadDir;
        this.clinicSettingsService = clinicSettingsService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(uploadDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new DoctorQueueFeatureInterceptor(clinicSettingsService, objectMapper))
                .addPathPatterns("/api/registration/doctor-queue/**");
    }
}
