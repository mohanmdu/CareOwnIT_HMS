package com.pms.settings.controller;

import com.pms.settings.dto.ClinicSettingsDto;
import com.pms.settings.service.ClinicSettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/settings/clinic")
public class ClinicSettingsController {

    private final ClinicSettingsService service;

    public ClinicSettingsController(ClinicSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public ClinicSettingsDto get() {
        return service.get();
    }

    @PutMapping
    public ClinicSettingsDto update(@Valid @RequestBody ClinicSettingsDto dto) {
        return service.update(dto);
    }

    @PostMapping("/logo")
    public ClinicSettingsDto uploadLogo(@RequestParam("file") MultipartFile file) {
        return service.uploadLogo(file);
    }

    @PostMapping("/favicon")
    public ClinicSettingsDto uploadFavicon(@RequestParam("file") MultipartFile file) {
        return service.uploadFavicon(file);
    }

    @PostMapping("/login-background")
    public ClinicSettingsDto uploadLoginBackground(@RequestParam("file") MultipartFile file) {
        return service.uploadLoginBackground(file);
    }
}
