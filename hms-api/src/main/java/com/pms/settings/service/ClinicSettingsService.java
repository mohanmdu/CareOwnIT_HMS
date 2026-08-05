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
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Read constantly (once per app bootstrap, per client), written rarely (an
 * admin tweaking branding) - cached in-memory (see CacheConfig) and evicted
 * on every write so a saved change is visible on the next page load without
 * needing a restart.
 *
 * Every cache key below is explicitly TenantContext.get() (this JVM's
 * current-request tenant, set by JwtAuthenticationFilter before this class
 * ever runs - see TenantContext's own doc comment) - NOT the method's
 * default no-args key. This app is one JVM process routing to many tenant
 * databases (see the "Database-per-Client Architecture" plan); get() takes
 * no parameters, so Spring's default SimpleKeyGenerator would produce the
 * exact same cache key regardless of which tenant's database the
 * underlying query actually hit. Without a tenant-qualified key here,
 * whichever client's user called get() first would have their hospital
 * name/logo/branding cached and served to every OTHER client's users too,
 * until some unrelated client's own settings update happened to evict it -
 * a real cross-tenant leak of exactly the branding data this whole screen
 * exists to keep per-client, not a hypothetical one (confirmed via direct
 * code inspection, not just guessed at).
 */
@Service
@Transactional(readOnly = true)
public class ClinicSettingsService {

    private static final String CACHE_NAME = "clinicSettings";
    private static final String CACHE_KEY = "T(com.pms.tenant.TenantContext).get()";

    private final ClinicSettingsRepository repository;
    private final FileStorageService fileStorageService;

    public ClinicSettingsService(ClinicSettingsRepository repository, FileStorageService fileStorageService) {
        this.repository = repository;
        this.fileStorageService = fileStorageService;
    }

    @Cacheable(value = CACHE_NAME, key = CACHE_KEY)
    public ClinicSettingsDto get() {
        return toDto(getOrThrow());
    }

    @Transactional
    @CacheEvict(value = CACHE_NAME, key = CACHE_KEY)
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
        settings.setBrandTextColor(dto.brandTextColor());
        settings.setMenuHoverIconColor(dto.menuHoverIconColor());
        return toDto(repository.save(settings));
    }

    @Transactional
    @CacheEvict(value = CACHE_NAME, key = CACHE_KEY)
    public ClinicSettingsDto uploadLogo(MultipartFile file) {
        ClinicSettings settings = getOrThrow();
        settings.setLogoPath(fileStorageService.store(file, "clinic"));
        return toDto(repository.save(settings));
    }

    @Transactional
    @CacheEvict(value = CACHE_NAME, key = CACHE_KEY)
    public ClinicSettingsDto uploadFavicon(MultipartFile file) {
        ClinicSettings settings = getOrThrow();
        settings.setFaviconPath(fileStorageService.store(file, "clinic"));
        return toDto(repository.save(settings));
    }

    /**
     * Also evicts "publicBranding" (see PublicBrandingService) - that cache is
     * keyed by clientCode, not by TenantContext, so this class's own
     * CACHE_KEY can't target just this tenant's entry; allEntries=true is
     * an acceptable blunt instrument here given that cache's short (45s)
     * TTL and low write frequency (an admin changing the login background).
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CACHE_NAME, key = CACHE_KEY),
            @CacheEvict(value = "publicBranding", allEntries = true, cacheManager = "licenseCacheManager")
    })
    public ClinicSettingsDto uploadLoginBackground(MultipartFile file) {
        ClinicSettings settings = getOrThrow();
        settings.setLoginBackgroundPath(fileStorageService.store(file, "clinic"));
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
                settings.getFontSizeScale(),
                settings.getBrandTextColor(),
                settings.getMenuHoverIconColor(),
                settings.getLoginBackgroundPath());
    }
}
