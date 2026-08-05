package com.pms.branding;

import com.pms.settings.entity.ClinicSettings;
import com.pms.settings.repository.ClinicSettingsRepository;
import com.pms.tenant.TenantContext;
import com.pms.tenant.entity.Client;
import com.pms.tenant.entity.ClientStatus;
import com.pms.tenant.repository.ClientRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * The one endpoint in this codebase reachable with no JWT at all (see
 * PublicBrandingController) - the multi-tenant login screen's first call,
 * made before any credentials are submitted, so it can show the right
 * hospital's logo/colors instead of a generic shell.
 *
 * Deliberately returns PublicBrandingDto.empty() - not a 404 - for an
 * unknown, suspended, or unprovisioned clientCode, for the same reason
 * LoginService's own multi-tenant login() collapses every failure into one
 * generic response: a distinguishable "this client code doesn't exist"
 * response would let this endpoint be used to enumerate which hospital
 * names are CareOwn customers, at zero cost to an anonymous caller.
 *
 * Cached short-TTL (see CacheConfig's licenseCacheManager, 45s) keyed on
 * the lowercased clientCode - this is called on every login-page load by
 * design, and a 45s staleness window on a logo/color is invisible to a
 * human reader. Evicted (bluntly - allEntries) whenever an admin uploads a
 * new login background (see ClinicSettingsService.uploadLoginBackground);
 * other branding fields (name/logo/colors) go stale for at most 45s after
 * an edit elsewhere, an accepted tradeoff for not wiring a second
 * cache-eviction path into every other ClinicSettingsService write.
 */
@Service
public class PublicBrandingService {

    private final ClientRepository clientRepository;
    private final ClinicSettingsRepository clinicSettingsRepository;

    public PublicBrandingService(ClientRepository clientRepository, ClinicSettingsRepository clinicSettingsRepository) {
        this.clientRepository = clientRepository;
        this.clinicSettingsRepository = clinicSettingsRepository;
    }

    @Cacheable(cacheNames = "publicBranding", cacheManager = "licenseCacheManager", key = "#clientCode.toLowerCase()")
    public PublicBrandingDto getByClientCode(String clientCode) {
        if (clientCode == null || clientCode.isBlank()) {
            return PublicBrandingDto.empty();
        }
        return clientRepository.findByCodeIgnoreCase(clientCode)
                .filter(client -> client.getStatus() == ClientStatus.ACTIVE)
                .map(this::brandingFor)
                .orElseGet(PublicBrandingDto::empty);
    }

    private PublicBrandingDto brandingFor(Client client) {
        return TenantContext.runAs(client.getId(), () -> clinicSettingsRepository.findById(ClinicSettings.SINGLETON_ID)
                .map(settings -> new PublicBrandingDto(
                        settings.getName(),
                        settings.getLogoPath(),
                        settings.getLoginBackgroundPath(),
                        settings.getThemePrimaryColor(),
                        settings.getThemeTertiaryColor()))
                .orElseGet(PublicBrandingDto::empty));
    }
}
