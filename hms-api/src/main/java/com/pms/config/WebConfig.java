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
 * Serves the PUBLIC half of uploaded files (consultant photos, CMS images,
 * clinic branding - see FileStorageService) as static files under
 * /uploads/** - the Angular dev server proxy.conf.json forwards that path
 * alongside /api. Deliberately scoped to app.upload-dir's "public"
 * subdirectory only, not the whole upload root - the "private" half (patient
 * photos, admission photos, patient report documents) must never be
 * reachable through this unauthenticated static handler; see
 * PrivateFileController for how those are served instead.
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
        String location = Path.of(uploadDir).resolve("public").toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new DoctorQueueFeatureInterceptor(clinicSettingsService, objectMapper))
                .addPathPatterns("/api/registration/doctor-queue/**");
    }
}
