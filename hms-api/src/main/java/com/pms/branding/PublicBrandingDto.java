package com.pms.branding;

/**
 * The narrow, deliberate set of fields shown on the login screen before a
 * user has authenticated - never a filtered view of ClinicSettingsDto (see
 * PublicBrandingService's own doc comment for why that distinction matters).
 */
public record PublicBrandingDto(
        String clinicName,
        String logoUrl,
        String loginBackgroundUrl,
        String themePrimaryColor,
        String themeTertiaryColor) {

    /**
     * Returned for an unknown, suspended, or unprovisioned clientCode -
     * identical in shape to a real client's response, so the login page
     * can't distinguish "no such client" from "client has no custom
     * branding" by response shape alone (see PublicBrandingService).
     */
    static PublicBrandingDto empty() {
        return new PublicBrandingDto(null, null, null, null, null);
    }
}
