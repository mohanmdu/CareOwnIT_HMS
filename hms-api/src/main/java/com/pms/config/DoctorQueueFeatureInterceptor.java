package com.pms.config;

import com.pms.common.ApiError;
import com.pms.settings.service.ClinicSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.ObjectMapper;

/**
 * The Doctor Queue Management module is an admin-toggleable add-on (Clinic
 * Settings) some hospital clients don't need - blocks the whole
 * /api/registration/doctor-queue/** surface with one check here rather than
 * repeating a guard in all ~15 DoctorQueueController handlers.
 */
public class DoctorQueueFeatureInterceptor implements HandlerInterceptor {

    private final ClinicSettingsService clinicSettingsService;
    private final ObjectMapper objectMapper;

    public DoctorQueueFeatureInterceptor(ClinicSettingsService clinicSettingsService, ObjectMapper objectMapper) {
        this.clinicSettingsService = clinicSettingsService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (clinicSettingsService.get().doctorQueueEnabled()) {
            return true;
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                "The Doctor Queue Management module is not enabled for this clinic.",
                request.getRequestURI());
        response.getWriter().write(objectMapper.writeValueAsString(error));
        return false;
    }
}
