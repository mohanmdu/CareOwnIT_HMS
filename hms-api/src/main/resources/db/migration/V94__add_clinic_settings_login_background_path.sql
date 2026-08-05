-- Login page background image, shown pre-authentication on the multi-tenant
-- login screen (see PublicBrandingController) - independent of logoPath/
-- faviconPath, same nullable-VARCHAR shape as those two.
ALTER TABLE clinic_settings
    ADD COLUMN login_background_path VARCHAR(255) NULL;
