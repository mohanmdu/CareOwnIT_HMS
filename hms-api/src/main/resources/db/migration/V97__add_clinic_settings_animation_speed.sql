-- Per-tenant control over the app's --hms-transition-fast/--hms-transition-base
-- durations (see ThemeService.applyTheme) - NONE/SUBTLE/STANDARD, same
-- curated-enum shape as font_size_scale/corner_radius_style rather than a
-- free numeric input.
ALTER TABLE clinic_settings
    ADD COLUMN animation_speed VARCHAR(16) NOT NULL DEFAULT 'STANDARD';
