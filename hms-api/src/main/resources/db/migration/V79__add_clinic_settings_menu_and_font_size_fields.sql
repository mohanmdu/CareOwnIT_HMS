ALTER TABLE clinic_settings
    ADD COLUMN menu_background_color VARCHAR(9) NULL,
    ADD COLUMN menu_text_color VARCHAR(9) NULL,
    ADD COLUMN menu_active_background_color VARCHAR(9) NULL,
    ADD COLUMN menu_active_text_color VARCHAR(9) NULL,
    ADD COLUMN menu_hover_background_color VARCHAR(9) NULL,
    ADD COLUMN font_size_scale VARCHAR(16) NOT NULL DEFAULT 'COMFORTABLE';
