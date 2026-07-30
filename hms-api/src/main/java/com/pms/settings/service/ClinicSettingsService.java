package com.pms.settings.service;

import com.pms.common.EntityNotFoundException;
import com.pms.common.FileStorageService;
import com.pms.settings.dto.ClinicSettingsDto;
import com.pms.settings.entity.ClinicSettings;
import com.pms.settings.entity.CornerRadiusStyle;
import com.pms.settings.entity.FontSizeScale;
import com.pms.settings.entity.ThemeMode;
import com.pms.settings.repository.ClinicSettingsRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Read constantly (once per app bootstrap, per client), written rarely (an
 * admin tweaking branding) - cached in-memory (see CacheConfig) and evicted
 * on every write so a saved change is visible on the next page load without
 * needing a restart.
 */
@Service
@Transactional(readOnly = true)
public class ClinicSettingsService {

    private static final String CACHE_NAME = "clinicSettings";

    private final ClinicSettingsRepository repository;
    private final FileStorageService fileStorageService;

    public ClinicSettingsService(ClinicSettingsRepository repository, FileStorageService fileStorageService) {
        this.repository = repository;
        this.fileStorageService = fileStorageService;
    }

    @Cacheable(CACHE_NAME)
    public ClinicSettingsDto get() {
        return toDto(getOrThrow());
    }

    @Transactional
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public ClinicSettingsDto update(ClinicSettingsDto dto) {
        ClinicSettings settings = getOrThrow();
        settings.setName(dto.name());
        settings.setAddress(dto.address());
        settings.setPhone(dto.phone());
        settings.setEmail(dto.email());
        settings.setTinNo(dto.tinNo());
        settings.setDlNo(dto.dlNo());
        settings.setWebsiteEnabled(dto.websiteEnabled() != null && dto.websiteEnabled());
        settings.setDoctorQueueEnabled(dto.doctorQueueEnabled() == null || dto.doctorQueueEnabled());
        settings.setDomain(dto.domain());
        settings.setThemePrimaryColor(dto.themePrimaryColor());
        settings.setThemeSecondaryColor(dto.themeSecondaryColor());
        settings.setSeoDefaultTitle(dto.seoDefaultTitle());
        settings.setSeoDefaultDescription(dto.seoDefaultDescription());
        settings.setSocialFacebookUrl(dto.socialFacebookUrl());
        settings.setSocialInstagramUrl(dto.socialInstagramUrl());
        settings.setSocialYoutubeUrl(dto.socialYoutubeUrl());
        settings.setWhatsappNumber(dto.whatsappNumber());
        settings.setThemeMode(dto.themeMode() != null ? dto.themeMode() : ThemeMode.LIGHT);
        settings.setThemeTertiaryColor(dto.themeTertiaryColor());
        settings.setFontFamily(dto.fontFamily());
        settings.setCornerRadiusStyle(dto.cornerRadiusStyle() != null ? dto.cornerRadiusStyle() : CornerRadiusStyle.ROUNDED);
        settings.setHeaderBackgroundColor(dto.headerBackgroundColor());
        settings.setFooterBackgroundColor(dto.footerBackgroundColor());
        settings.setFooterText(dto.footerText());
        settings.setMenuBackgroundColor(dto.menuBackgroundColor());
        settings.setMenuTextColor(dto.menuTextColor());
        settings.setMenuActiveBackgroundColor(dto.menuActiveBackgroundColor());
        settings.setMenuActiveTextColor(dto.menuActiveTextColor());
        settings.setMenuHoverBackgroundColor(dto.menuHoverBackgroundColor());
        settings.setMenuIconColor(dto.menuIconColor());
        settings.setMenuChevronColor(dto.menuChevronColor());
        settings.setMenuHoverTextColor(dto.menuHoverTextColor());
        settings.setFontSizeScale(dto.fontSizeScale() != null ? dto.fontSizeScale() : FontSizeScale.COMFORTABLE);
        return toDto(repository.save(settings));
    }

    @Transactional
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public ClinicSettingsDto uploadLogo(MultipartFile file) {
        ClinicSettings settings = getOrThrow();
        settings.setLogoPath(fileStorageService.store(file, "clinic"));
        return toDto(repository.save(settings));
    }

    @Transactional
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public ClinicSettingsDto uploadFavicon(MultipartFile file) {
        ClinicSettings settings = getOrThrow();
        settings.setFaviconPath(fileStorageService.store(file, "clinic"));
        return toDto(repository.save(settings));
    }

    private ClinicSettings getOrThrow() {
        return repository.findById(ClinicSettings.SINGLETON_ID)
                .orElseThrow(() -> new EntityNotFoundException("Clinic settings not found"));
    }

    private ClinicSettingsDto toDto(ClinicSettings settings) {
        return new ClinicSettingsDto(
                settings.getName(),
                settings.getAddress(),
                settings.getPhone(),
                settings.getEmail(),
                settings.getLogoPath(),
                settings.getTinNo(),
                settings.getDlNo(),
                settings.isWebsiteEnabled(),
                settings.isDoctorQueueEnabled(),
                settings.getDomain(),
                settings.getThemePrimaryColor(),
                settings.getThemeSecondaryColor(),
                settings.getFaviconPath(),
                settings.getSeoDefaultTitle(),
                settings.getSeoDefaultDescription(),
                settings.getSocialFacebookUrl(),
                settings.getSocialInstagramUrl(),
                settings.getSocialYoutubeUrl(),
                settings.getWhatsappNumber(),
                settings.getThemeMode(),
                settings.getThemeTertiaryColor(),
                settings.getFontFamily(),
                settings.getCornerRadiusStyle(),
                settings.getHeaderBackgroundColor(),
                settings.getFooterBackgroundColor(),
                settings.getFooterText(),
                settings.getMenuBackgroundColor(),
                settings.getMenuTextColor(),
                settings.getMenuActiveBackgroundColor(),
                settings.getMenuActiveTextColor(),
                settings.getMenuHoverBackgroundColor(),
                settings.getMenuIconColor(),
                settings.getMenuChevronColor(),
                settings.getMenuHoverTextColor(),
                settings.getFontSizeScale());
    }
}
