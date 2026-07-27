package com.pms.settings.dto;

import com.pms.settings.entity.CornerRadiusStyle;
import com.pms.settings.entity.FontSizeScale;
import com.pms.settings.entity.ThemeMode;
import jakarta.validation.constraints.NotBlank;

public record ClinicSettingsDto(
        @NotBlank String name,
        String address,
        String phone,
        String email,
        String logoUrl,
        String tinNo,
        String dlNo,
        Boolean websiteEnabled,
        String domain,
        String themePrimaryColor,
        String themeSecondaryColor,
        String faviconUrl,
        String seoDefaultTitle,
        String seoDefaultDescription,
        String socialFacebookUrl,
        String socialInstagramUrl,
        String socialYoutubeUrl,
        String whatsappNumber,
        ThemeMode themeMode,
        String themeTertiaryColor,
        String fontFamily,
        CornerRadiusStyle cornerRadiusStyle,
        String headerBackgroundColor,
        String footerBackgroundColor,
        String footerText,
        String menuBackgroundColor,
        String menuTextColor,
        String menuActiveBackgroundColor,
        String menuActiveTextColor,
        String menuHoverBackgroundColor,
        String menuIconColor,
        String menuChevronColor,
        String menuHoverTextColor,
        FontSizeScale fontSizeScale) {
}
