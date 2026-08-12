package com.pms.settings.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Singleton (id always 1, seeded by V18) hospital branding used on printed
 * receipts - this product deploys to multiple hospital clients, so the name/
 * address/logo shown on a receipt must be admin-editable per deployment
 * rather than hardcoded in the frontend template.
 */
@Entity
@Table(name = "clinic_settings")
@Getter
@Setter
@NoArgsConstructor
public class ClinicSettings {

    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String address;

    @Column(length = 64)
    private String phone;

    private String email;

    @Column(name = "logo_path")
    private String logoPath;

    @Column(name = "tin_no", length = 50)
    private String tinNo;

    @Column(name = "dl_no", length = 50)
    private String dlNo;

    @Column(name = "website_enabled", nullable = false)
    private boolean websiteEnabled = false;

    /** Add-on toggle for the Doctor Queue Management module (Reception Check-In, Doctor Dashboard) - some clients don't need it. */
    @Column(name = "doctor_queue_enabled", nullable = false)
    private boolean doctorQueueEnabled = true;

    private String domain;

    @Column(name = "theme_primary_color", length = 9)
    private String themePrimaryColor;

    @Column(name = "theme_secondary_color", length = 9)
    private String themeSecondaryColor;

    @Column(name = "favicon_path")
    private String faviconPath;

    @Column(name = "seo_default_title")
    private String seoDefaultTitle;

    @Column(name = "seo_default_description", length = 500)
    private String seoDefaultDescription;

    @Column(name = "social_facebook_url")
    private String socialFacebookUrl;

    @Column(name = "social_instagram_url")
    private String socialInstagramUrl;

    @Column(name = "social_youtube_url")
    private String socialYoutubeUrl;

    @Column(name = "whatsapp_number", length = 20)
    private String whatsappNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme_mode", nullable = false, length = 16)
    private ThemeMode themeMode = ThemeMode.LIGHT;

    @Column(name = "theme_tertiary_color", length = 9)
    private String themeTertiaryColor;

    @Column(name = "font_family", length = 120)
    private String fontFamily;

    @Enumerated(EnumType.STRING)
    @Column(name = "corner_radius_style", nullable = false, length = 16)
    private CornerRadiusStyle cornerRadiusStyle = CornerRadiusStyle.ROUNDED;

    @Column(name = "header_background_color", length = 9)
    private String headerBackgroundColor;

    @Column(name = "footer_background_color", length = 9)
    private String footerBackgroundColor;

    @Column(name = "footer_text", length = 500)
    private String footerText;

    @Column(name = "menu_background_color", length = 9)
    private String menuBackgroundColor;

    @Column(name = "menu_text_color", length = 9)
    private String menuTextColor;

    @Column(name = "menu_active_background_color", length = 9)
    private String menuActiveBackgroundColor;

    @Column(name = "menu_active_text_color", length = 9)
    private String menuActiveTextColor;

    @Column(name = "menu_hover_background_color", length = 9)
    private String menuHoverBackgroundColor;

    @Column(name = "menu_icon_color", length = 9)
    private String menuIconColor;

    @Column(name = "menu_chevron_color", length = 9)
    private String menuChevronColor;

    @Column(name = "menu_hover_text_color", length = 9)
    private String menuHoverTextColor;

    @Enumerated(EnumType.STRING)
    @Column(name = "font_size_scale", nullable = false, length = 16)
    private FontSizeScale fontSizeScale = FontSizeScale.COMFORTABLE;

    /** Independent from themePrimaryColor - see V92's own doc comment. */
    @Column(name = "brand_text_color", length = 9)
    private String brandTextColor;

    /** Independent from menuIconColor - what a leaf nav item's icon becomes on hover (see V93's own doc comment). */
    @Column(name = "menu_hover_icon_color", length = 9)
    private String menuHoverIconColor;

    /** Shown pre-authentication on the multi-tenant login screen - see V94's own doc comment and PublicBrandingController. */
    @Column(name = "login_background_path", length = 255)
    private String loginBackgroundPath;

    /** See AnimationSpeed's own doc comment. */
    @Enumerated(EnumType.STRING)
    @Column(name = "animation_speed", nullable = false, length = 16)
    private AnimationSpeed animationSpeed = AnimationSpeed.STANDARD;
}
